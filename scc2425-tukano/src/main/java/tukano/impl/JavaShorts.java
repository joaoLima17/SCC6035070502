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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import utils.DB;
import utils.JSON;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final int EXPIRATION_TIME = 60 * 60; // 1 hour
	private static final boolean POSTGRE = true;
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
				if (POSTGRE)
					return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));

				Result<Short> result = errorOrValue(CosmosDB.insertOne(shrt, "shorts"),
						s -> s.copyWithLikes_And_Token(0));

				/*
				 * if (result.isOK()){
				 * errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
				 * t.exec();
				 * }
				 * else
				 * t.discard();
				 */
				return result;
			}
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);
		if (POSTGRE) {
			var query = format("SELECT count(*) FROM PostgreLikes l WHERE l.shortId = '%s'", shortId);
			var likes = DB.sql(query, Long.class);
			return errorOrValue(DB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
		}
		var query = format("SELECT VALUE COUNT(1) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = CosmosDB.query(query, Long.class, "shorts").value().toList();
		return errorOrValue(getOne(shortId, Short.class, "shorts"), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			if (POSTGRE) {
				return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

					return DB.transaction(hibernate -> {

						hibernate.remove(shrt);

						var query = format("DELETE FROM PostgreLikes l WHERE l.shortId = '%s'", shortId);
						hibernate.createNativeQuery(query, Likes.class).executeUpdate();

						JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
					});
				});
			}
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

				String redisId = "shorts: " + shortId;

				if (jedis.exists(redisId))
					jedis.del(redisId);
			}
			CosmosDB.deleteOne(shrt, "shorts");

			return JavaBlobs.getInstance().delete(shortId, Token.get(shortId));
		});
	};

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));
		if (POSTGRE) {
			var query = format("SELECT s.shortId FROM PostgreShort s WHERE s.ownerId = '%s'", userId);
			return errorOrValue(okUser(userId), DB.sql(query, String.class));
		}
		var query = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
		List<Short> shorts = CosmosDB.query(query, Short.class, "shorts").value().toList();

		List<String> ids = shorts.stream()
				.map(Short::getShortId)
				.collect(Collectors.toList());

		return errorOrValue(okUser(userId), ids);
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId2, userId1);
			if (POSTGRE)
				return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
			var query = format("SELECT * FROM Following f WHERE f.id = '%s'", userId1 + '|' + userId2);

			var s = CosmosDB.query(query, Following.class, "follow").value().toList();
			if ((!isFollowing && s.isEmpty()) || (isFollowing && !s.isEmpty()))
				return ok();
			Result<Void> res = errorOrVoid(okUser(userId2),
					isFollowing ? CosmosDB.insertOne(f, "follow") : CosmosDB.deleteOne(f, "follow"));
			return res;
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));
		if (POSTGRE) {
			var query = format("SELECT f.follower FROM PostgreFollowing f WHERE f.followee = '%s'", userId);
			return errorOrValue(okUser(userId, password), DB.sql(query, String.class));
		}
		var query = format("SELECT VALUE f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue(okUser(userId, password), CosmosDB.query(query, String.class, "follow").value().toList());
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			if (POSTGRE)
				return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
			return errorOrVoid(okUser(userId, password),
					isLiked ? CosmosDB.insertOne(l, "like") : CosmosDB.deleteOne(l, "like"));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			if (POSTGRE) {
				var query = format("SELECT l.userId FROM PostgreLikes l WHERE l.shortId = '%s'", shortId);
				return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, String.class));
			}
			var query = format("SELECT VALUE l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password),
					CosmosDB.query(query, String.class, "like").value().toList());
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));
		if (POSTGRE) {
			final var QUERY_FMT = """
					SELECT s.shortId, s.timestamp FROM PostgreShort s WHERE s.ownerId = '%s'
					UNION
					SELECT ps.shortId, ps.timestamp FROM PostgreShort ps
					JOIN PostgreFollowing f ON f.followee = ps.ownerId WHERE f.follower = '%s'
					ORDER BY timestamp DESC
					""";

			return errorOrValue(okUser(userId, password), DB.sql(format(QUERY_FMT, userId, userId), String.class));
		}
		var r = okUser(userId, password);
		if (!r.isOK())
			return error(r.error());

		List<String> followeeIds = followers(userId, password).value();
		List<Short> shortList = new ArrayList<>();

		for (String id : followeeIds) {
			var query2 = String.format("SELECT * FROM Short s WHERE s.ownerId = '%s'", id);
			List<Short> followeeShorts = CosmosDB.query(query2, Short.class, "shorts").value().toList();

			shortList.addAll(followeeShorts);
		}

		shortList.sort(Comparator.comparing(Short::getTimestamp).reversed());

		List<String> sortedFeed = shortList.stream()
				.map(Short::getShortId)
				.collect(Collectors.toList());

		return ok(sortedFeed);
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else {
			return error(res.error());
		}
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if (!Token.isValid(token, userId))
			return error(FORBIDDEN);
		if (POSTGRE)
			return DB.transaction((hibernate) -> {

				// delete shorts
				var query1 = format("DELETE FROM PostgreShort s WHERE s.ownerId = '%s'", userId);
				hibernate.createNativeQuery(query1, Short.class).executeUpdate();

				// delete follows
				var query2 = format("DELETE FROM PostgreFollowing f WHERE f.follower = '%s' OR f.followee = '%s'",
						userId, userId);
				hibernate.createNativeQuery(query2, Following.class).executeUpdate();

				// delete likes
				var query3 = format("DELETE FROM PostgreLikes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId,
						userId);
				hibernate.createNativeQuery(query3, Likes.class).executeUpdate();

			});
		deleteAllLikes(userId, password);
		deleteAllFollows(userId, password);
		List<String> shorts = getShorts(userId).value();

		for (String id : shorts) {
			deleteShort(id, password);
		}

		return Result.ok();
	}

	private Result<Void> deleteAllLikes(String userId, String password) {
		Log.info(() -> format("deleteAllLikes : userId = %s, password = %s\n", userId, password));

		var query1 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s'", userId);
		List<Likes> likesReceived = CosmosDB.query(query1, Likes.class, "like").value().toList();

		var query2 = format("SELECT * FROM Likes l WHERE l.userId = '%s'", userId);
		List<Likes> likesGiven = CosmosDB.query(query2, Likes.class, "like").value().toList();

		for (Likes like : likesReceived) {
			like(like.getShortId(), like.getUserId(), false, password);
		}
		for (Likes like : likesGiven) {
			like(like.getShortId(), like.getUserId(), false, password);
		}

		return Result.ok();
	}

	private Result<Void> deleteAllFollows(String userId, String password) {
		Log.info(() -> format("deleteAllFollows : userId = %s, password = %s\n", userId, password));

		var query1 = format("SELECT * FROM Following f WHERE f.follower = '%s'", userId);
		List<Following> followers = CosmosDB.query(query1, Following.class, "follow").value().toList();

		var query2 = format("SELECT * FROM Following f WHERE f.followee = '%s'", userId);
		List<Following> followees = CosmosDB.query(query2, Following.class, "follow").value().toList();

		for (Following f : followers) {
			follow(userId, f.getFollowee(), false, password);
		}

		for (Following f : followees) {
			CosmosDB.deleteOne(f, "follow");
		}

		return Result.ok();
	}

}