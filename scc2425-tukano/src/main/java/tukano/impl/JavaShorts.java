package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static utils.CosmosDB.getOne;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import cache.RedisCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.CosmosDB;
import utils.JSON;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final int EXPIRATION_TIME = 60 * 60; // 1 hour

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				var redisId = "shorts: " + shortId;
				if (jedis.exists(redisId)) {
					return Result.error(FORBIDDEN);
				}

				Transaction t = jedis.multi();

				var userJSON = JSON.encode(user);
				t.set(redisId, userJSON);
				t.expire(redisId, EXPIRATION_TIME);
				Result<Short> result = errorOrValue(CosmosDB.insertOne(shrt, "short"), s -> s.copyWithLikes_And_Token(0));

				if (result.isOK())
					t.exec();
				else
					t.discard();

				return result;
			}
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		var query = format("SELECT VALUE COUNT(1) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = CosmosDB.query(query, Long.class, "short").value().toList();
		return errorOrValue(getOne(shortId, Short.class, "short"), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			/*
			 * return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
			 * 
			 * return DB.transaction( hibernate -> {
			 * 
			 * hibernate.remove( shrt);
			 * 
			 * var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
			 * hibernate.createNativeQuery( query, Likes.class).executeUpdate();
			 * 
			 * JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
			 * });
			 */
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				String redisId = "shorts: " + shortId;
				
				if (jedis.exists(redisId)) 
					jedis.del(redisId);
			}
			CosmosDB.deleteOne(shrt, "short");
			
			return JavaBlobs.getInstance().delete(shortId, Token.get(shortId));
		});
	};

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue(okUser(userId), CosmosDB.query(query, String.class, "short").value().toList());
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId2, userId1);
			//Result<Object> res1 = CosmosDB.insertOne(f);
			//Result<Follow> resTeste = CosmosDB.getOne(f);
			//Log.info("RESULTADO DO INSERT ONEEEEEEEEE:::::::: " + res1);
			Result<Void> res = errorOrVoid(okUser(userId2), isFollowing ? CosmosDB.insertOne(f, "follow") : CosmosDB.deleteOne(f, "follow"));
			return res;
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue(okUser(userId, password), CosmosDB.query(query, String.class, "follow").value().toList());
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid(okUser(userId, password), isLiked ? CosmosDB.insertOne(l, "like") : CosmosDB.deleteOne(l, "like"));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password),
					CosmosDB.query(query, String.class, "like").value().toList());
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		//TODO: mudar query
		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'
				UNION
				SELECT s.shortId, s.timestamp FROM Short s, Following f
					WHERE
						f.followee = s.ownerId AND f.follower = '%s'
				ORDER BY s.timestamp DESC""";

		return errorOrValue(okUser(userId, password),
				CosmosDB.query(format(QUERY_FMT, userId, userId), String.class, "short").value().toList());
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else {
			Log.info("RESPOSTA DO OK USER: " + res.error().toString());
			return error(res.error());
		}
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if (!Token.isValid(token, userId))
			return error(FORBIDDEN);

		/*
		 * return CosmosDB.transaction( (hibernate) -> {
		 * 
		 * //delete shorts
		 * var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
		 * hibernate.createQuery(query1, Short.class).executeUpdate();
		 * 
		 * //delete follows
		 * var query2 =
		 * format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'",
		 * userId, userId);
		 * hibernate.createQuery(query2, Following.class).executeUpdate();
		 * 
		 * //delete likes
		 * var query3 =
		 * format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId,
		 * userId);
		 * hibernate.createQuery(query3, Likes.class).executeUpdate();
		 * 
		 * });
		 */
		var query1 = format("SELECT * FROM Short s WHERE s.userId = '%s'", userId);
		List<Short> query = CosmosDB.query(query1, Short.class, "short").value().toList();
		
		for (Short shrt : query) {
			CosmosDB.deleteOne(shrt, "short");
		}

		//TODO: delete dos follows

		//TODO: delete dos likes

		return Result.ok();
	}

}