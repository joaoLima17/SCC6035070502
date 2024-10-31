package utils;

import java.util.stream.Stream;

import db.CosmosDBLayer;

import tukano.api.Result;

public class CosmosDB {

	public static <T> Result<Stream<T>> query(String query, Class<T> clazz) {
		return CosmosDBLayer.getInstance().query(clazz, query);
	}
	
	public static <T> Result<Stream<T>> query(Class<T> clazz, String fmt, Object ... args) {
		return CosmosDBLayer.getInstance().query(clazz, String.format(fmt, args));
	}
	
	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return CosmosDBLayer.getInstance().getOne(id, clazz);
	}
	
	public static <T> Result<?> deleteOne(T obj) {//Result<?>
		return CosmosDBLayer.getInstance().deleteOne(obj);
	}
	
	public static <T> Result<T> updateOne(T obj) {
		return CosmosDBLayer.getInstance().updateOne(obj);
	}
	
	public static <T> Result<T> insertOne( T obj) {
		return CosmosDBLayer.getInstance().insertOne(obj);
	}
	
	public static void close() {
		CosmosDBLayer.getInstance().close();
	}
}