package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import cache.RedisCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.CosmosDB;
import utils.JSON;

public class JavaUsers implements Users {

	private static final int EXPIRATION_TIME = 60 * 60; // 1 hour

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		String userId = user.getUserId();
		String redisId = "users: " + userId;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			if (jedis.exists(redisId)) {
				return Result.error(CONFLICT);
			}

			Transaction t = jedis.multi();

			var userJSON = JSON.encode(user);
			t.set(redisId, userJSON);
			t.expire(redisId, EXPIRATION_TIME);
			Result<String> result = errorOrValue(CosmosDB.insertOne(user, "user"), userId);

			if (result.isOK())
				t.exec();
			else
				t.discard();

			return result;
		}
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String redisId = "users: " + userId;

			String user = jedis.get(redisId);
			if (user != null) {
				return validatedUserOrError(ok(JSON.decode(user, User.class)), pwd);
			}
			Result<User> result = validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd);
			if (result.isOK()) {
				var userJSON = JSON.encode(result.value());
				jedis.set(redisId, userJSON);
				jedis.expire(redisId, EXPIRATION_TIME);
				return result;
			} else
				return error(NOT_FOUND);
		}
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String redisId = "users: " + userId;

			String redis = jedis.get(redisId);

			if (redis != null) {

				Result<User> validated = validatedUserOrError(ok(JSON.decode(redis, User.class)), pwd);

				if (!validated.isOK()) return error(FORBIDDEN);

				User redisUser = JSON.decode(redis, User.class);
				jedis.del(redisId);

				User updatedUser = redisUser.updateFrom(other);
				jedis.set(redisId, JSON.encode(updatedUser));

				return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd),
						user -> CosmosDB.updateOne(updatedUser, "user"));
			}
			// caso nao esteja na cache, adicionar
		}

		return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd),
				user -> CosmosDB.updateOne(user.updateFrom(other), "user"));
	}

	@Override
	public Result<?> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String redisId = "users: " + userId;
			String redis = jedis.get(redisId);

			if (redis != null && validatedUserOrError(ok(JSON.decode(redis, User.class)), pwd).isOK()) {
				User redisUser = JSON.decode(redis, User.class);
				if (redisUser != null) {
					jedis.del(redisId);
				}
			}
		}

		return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return CosmosDB.deleteOne(user, "user");
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));
		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = CosmosDB.query(query, User.class, "user")
				.value()
				.map(User::copyWithoutPassword)
				.toList();
		return ok(hits);
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK())
			return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}
}
