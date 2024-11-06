package utils;

import java.util.stream.Stream;

import db.CosmosDBLayer;

import tukano.api.Result;

public class CosmosDB {

	public static <T> Result<Stream<T>> query(String query, Class<T> clazz, String type) {
		return CosmosDBLayer.getInstance().query(clazz, query, type);
	}
	
	public static <T> Result<Stream<T>> query(Class<T> clazz, String fmt, String type, Object ... args ) {
		return CosmosDBLayer.getInstance().query(clazz, String.format(fmt, args), type);
	}
	
	public static <T> Result<T> getOne(String id, Class<T> clazz, String type) {
		return CosmosDBLayer.getInstance().getOne(id, clazz, type);
	}
	
	public static <T> Result<?> deleteOne(T obj, String type) {//Result<?>
		return CosmosDBLayer.getInstance().deleteOne(obj, type);
	}
	
	public static <T> Result<T> updateOne(T obj, String type) {
		return CosmosDBLayer.getInstance().updateOne(obj, type);
	}
	
	public static <T> Result<T> insertOne( T obj, String type) {
		return CosmosDBLayer.getInstance().insertOne(obj, type);
	}
	
	public static void close() {
		CosmosDBLayer.getInstance().close();
	}
}