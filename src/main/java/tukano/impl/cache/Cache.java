package tukano.impl.cache;

import tukano.api.Result;

public class Cache {

    public static <T> Result<T> getOne(String id, Class<T> clazz) {
        return RedisCache.getRedisCache().getOne(id, clazz); // get one container id string, class = User
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> deleteOne(T obj) {
        return (Result<T>) RedisCache.getRedisCache().deleteOne(obj);
    }

    public static <T> Result<?> updateOne(T obj) {
        return RedisCache.getRedisCache().updateOne(obj);
    }

    public static <T> Result<T> insertOne(T obj) {
        System.out.println("HELLO IM AT DB");
        return Result.errorOrValue(RedisCache.getRedisCache().insertOne(obj), obj);
    }
}
