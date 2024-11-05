package tukano.impl.cache;

import java.util.function.Supplier;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Short;

import tukano.api.Result.ErrorCode;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import utils.JSON;

public class RedisCache {
    private static final String RedisHostname = "redis70525northeurope.redis.cache.windows.net";
    private static final String RedisKey = "XJule52lhBYg9TkFVIyZHoBvazpQkK2F4AzCaFewXUc=";
    private static final int REDIS_PORT = 6380;
    private static final int REDIS_TIMEOUT = 1000;
    private static final boolean Redis_USE_TLS = true;

    private static JedisPool jedis_instance;
    private static RedisCache redis_instance;
    private static String MOST_RECENT_USERS_LIST = "MostRecentUsers";
    private static String MOST_RECENT_SHORTS_LIST = "MostRecentShorts";
    private static String MOST_RECENT_LIKES_LIST = "MostRecentLikes";
    private static String MOST_RECENT_FOLLOWS_LIST = "MostRecentFollows";

    public static synchronized RedisCache getRedisCache() {
        if (redis_instance != null)
            return redis_instance;

        redis_instance = new RedisCache();
        return redis_instance;
    }

    public synchronized static JedisPool getCachePool() {
        if (jedis_instance != null)
            return jedis_instance;
        System.out.println("WILL RETURN NEW CACHE");
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        jedis_instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
        return jedis_instance;
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        System.out.println("GET ONE CACHE LAYER");
        var cId = clazz.getName();
        System.out.println("class name on redis is ");
        System.out.println(cId);
        return tryCatch(() -> {
            try (Jedis jedis = getCachePool().getResource()) {
                var key = cId.toLowerCase() + ":" + id;
                var value = jedis.get(key);
                return value != null ? JSON.decode(value, clazz) : null;
            }
        }, cId);
    }

    public <T> Result<?> deleteOne(T obj) {
        var cl = obj.getClass().getName();

        return tryCatch(() -> {
            try (Jedis jedis = getCachePool().getResource()) {
                var key = cl.toLowerCase() + ":" + getObjectId(obj);
                jedis.del(key);

                var result = jedis.get(key);
                return JSON.decode(result, obj.getClass());
            }
        }, cl);
    }

    public <T> Result<?> updateOne(T obj) {
        var cl = obj.getClass().getName();

        return tryCatch(() -> {
            try (Jedis jedis = getCachePool().getResource()) {
                var key = cl.toLowerCase() + ":" + getObjectId(obj);
                var value = JSON.encode(obj);
                jedis.set(key, value);

                var result = jedis.get(key);
                return JSON.decode(result, obj.getClass());
            }
        }, cl);
    }

    public <T> Result<?> insertOne(T obj) {
        var cId = obj.getClass().getName();
        System.out.println(cId);
        return tryCatch(() -> {
            try (Jedis jedis = getCachePool().getResource()) {
                var key = cId.toLowerCase() + ":" + getObjectId(obj);
                var value = JSON.encode(obj);
                jedis.set(key, value);

                // depende do tipo de obj
                var list = getObjectList(obj);
                jedis.lpush(list, value);
                if (jedis.llen(list) > 5) {
                    jedis.ltrim(list, 0, 4);
                }

                var result = jedis.get(key);
                return JSON.decode(result, obj.getClass());
            }
        }, cId);
    }

    private <T> String getObjectList(T obj) {
        switch (obj.getClass().getName()) {
            case "tukano.api.User":
                return MOST_RECENT_USERS_LIST;
            case "tukano.api.Short":
                return MOST_RECENT_SHORTS_LIST;
            case "tukano.impl.data.Likes":
                return MOST_RECENT_LIKES_LIST;
            case "tukano.impl.data.Following":
                return MOST_RECENT_FOLLOWS_LIST;
            default:
                return null;
        }
    }

    private <T> String getObjectId(T obj) {
        switch (obj.getClass().getName()) {
            case "tukano.api.User":
                return ((User) obj).getUserId();
            case "tukano.api.Short":
                return ((Short) obj).getShortId();
            case "tukano.impl.data.Likes":
                return ((Likes) obj).getShortId() + ":" + ((Likes) obj).getUserId();
            case "tukano.impl.data.Following":
                return ((Following) obj).getFollowee() + ":" + ((Following) obj).getFollower();
            default:
                return null;
        }
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc, String classString) {
        try {
            return Result.ok(supplierFunc.get());
        } catch (JedisException je) {
            // ce.printStackTrace();
            System.out.println(je);
            System.out.println("YOYO CACHE EXCEPTION AQUI");
            return Result.error(ErrorCode.INTERNAL_ERROR);
        } catch (Exception x) {
            System.out.println("EXCEPTION X TRY CATCH");
            x.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
