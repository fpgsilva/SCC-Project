package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;


import tukano.api.Result;

public class DB {

	public static <T> Result<List<T>> sql(String query, Class<T> clazz) {
		return CosmosDBLayer.getInstance().query(clazz, query);
	}

	public static <T> Result<List<T>> sql(Class<T> clazz, String fmt, Object... args) {
		return CosmosDBLayer.getInstance().query(clazz, String.format(fmt, args));
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return CosmosDBLayer.getInstance().getOne(id, clazz); // get one container id string, class = User
	}

	@SuppressWarnings("unchecked")
	public static <T> Result<T> deleteOne(T obj) {
		return (Result<T>) CosmosDBLayer.getInstance().deleteOne(obj);
	}

	public static <T> Result<T> updateOne(T obj) {
		return CosmosDBLayer.getInstance().updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj) {
		return Result.errorOrValue(CosmosDBLayer.getInstance().insertOne(obj), obj);
	}

	public static <T> Result<T> transaction(Consumer<Session> c) {
		return Hibernate.getInstance().execute(c::accept);
	}

	public static <T> Result<T> transaction(Function<Session, Result<T>> func) {
		return Hibernate.getInstance().execute(func);
	}
}
