package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;

import static tukano.api.Result.ErrorCode.FORBIDDEN;


import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import cache.RedisCache;
import jakarta.ws.rs.core.Response;
//import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import tukano.api.Authentication;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.CosmosDB;
import utils.DB;
import utils.JSON;


public class JavaUsers implements Users {

	private static final int EXPIRATION_TIME = 60 * 60; // 1 hour
	private static final boolean POSTGRE = true;
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
				jedis.del(redisId);
			}

			var userJSON = JSON.encode(user);
			jedis.set(redisId, userJSON);
			jedis.expire(redisId, EXPIRATION_TIME);
			if(POSTGRE)
			return errorOrValue( DB.insertOne( user), user.getUserId() );
			else
			return errorOrValue(CosmosDB.insertOne(user, "user"), userId);
		}
	}

	@Override
	public Response login(String userId, String password) {
		return Authentication.login(userId, password);
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String redisId = "users: " + userId;
			Log.info("CHEGUEI ATE A CACHE");
			String user = jedis.get(redisId);
			Log.info("ULTRAPASSEI A CACHE");
			if (user != null) {
				Log.info("ESTAVA NA CACHE");
				return validatedUserOrError(ok(JSON.decode(user, User.class)), pwd);
			}
			Log.info("NÃO ESTAVA NA CACHE");
			Result<User> res;
			if(POSTGRE)
				res = DB.getOne(userId, User.class);
			else
				res = CosmosDB.getOne(userId, User.class, "user");
			Result<User> result = validatedUserOrError(res, pwd);
			if (result.isOK()) {
				var userJSON = JSON.encode(result.value());
				jedis.set(redisId, userJSON);
				jedis.expire(redisId, EXPIRATION_TIME);
				return result;
			} else {
				return error(result.error());
			}
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

				if (!validated.isOK())
					return error(FORBIDDEN);

				User redisUser = JSON.decode(redis, User.class);
				jedis.del(redisId);

				User updatedUser = redisUser.updateFrom(other);
				jedis.set(redisId, JSON.encode(updatedUser));
				if(POSTGRE)
				return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd),
						user -> DB.updateOne(updatedUser ));
				return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd),
						user -> CosmosDB.updateOne(updatedUser, "user"));
			}
		}
		
		if(POSTGRE)
		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd),
					user ->DB.updateOne(user.updateFrom(other)));
		else 
		return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd),
				user -> CosmosDB.updateOne(user.updateFrom(other), "user"));
	}

	@Override
	public Result<?> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);
			if(POSTGRE)
			return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

				Executors.defaultThreadFactory().newThread( () -> {
					JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
					JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));

					try (Jedis jedis = RedisCache.getCachePool().getResource()) {

						String redisId = "users: " + userId;
						String redis = jedis.get(redisId);
	
						if (redis != null && validatedUserOrError(ok(JSON.decode(redis, User.class)), pwd).isOK()) {
							jedis.del(redisId);
						}
					}
				}).start();
				
				return DB.deleteOne( user);
			});
		return errorOrResult(validatedUserOrError(CosmosDB.getOne(userId, User.class, "user"), pwd), user -> {

			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));


				try (Jedis jedis = RedisCache.getCachePool().getResource()) {

					String redisId = "users: " + userId;
					String redis = jedis.get(redisId);

					if (redis != null && validatedUserOrError(ok(JSON.decode(redis, User.class)), pwd).isOK()) {
						jedis.del(redisId);
					}
				}
			}).start();

			return CosmosDB.deleteOne(user, "user");
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));
		if(POSTGRE){
			if (pattern == null)
				return ok(DB.sql("SELECT * FROM PostgreUser", User.class)
						.stream()
						.map(User::copyWithoutPassword)
						.toList());

			var query = format("SELECT * FROM PostgreUser u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
			var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

			return ok(hits);
		}

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
