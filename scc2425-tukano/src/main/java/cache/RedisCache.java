package cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import tukano.api.Session;
import utils.JSON;

public class RedisCache {
	private static final String RedisHostname = "redis";
	
	private static final int REDIS_PORT = 6379; 
	private static final int REDIS_TIMEOUT = 1000;

	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;
		
		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT);
		return instance;
	}

	public static void putSession(String uid, Session s) {
		try (Jedis jedis = instance.getResource()) {

			String redisId = "cookie: " + uid;
			var sessionJSON = JSON.encode(s);
			jedis.set(redisId, sessionJSON);
			jedis.expire(redisId, 3600);
		}
	}
	public static Session getSession(String uid) {
		try (Jedis jedis = instance.getResource()) {

			String redisId = "cookie: " + uid;
			return JSON.decode(jedis.get(redisId), Session.class);
		}
	}
}
