package db;

import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Session;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CosmosDBLayer {

	
	private static Logger Log = Logger.getLogger(CosmosDBLayer.class.getName());
	private static final String CONNECTION_URL = "https://scc60350.documents.azure.com:443/"; // replace with your own
	private static final String DB_KEY = "RaSUWAOvvbWuL4LVXHEQGjeLSrZig4rgXH9FZD1YxSGDGbW4oIGVvUymjJSjRiLFaoCZjQyXb0tHACDbTLQFlQ==";
	private static final String DB_NAME = "scc2324";
	private static final String USERS_CONTAINER = "users";
	private static final String FOLLOWS_CONTAINER = "follows";
	private static final String LIKES_CONTAINER = "likes";
	Map<String, Session> sessions = new ConcurrentHashMap<>();
	
	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if( instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
		         .endpoint(CONNECTION_URL)
		         .key(DB_KEY)
		         .directMode()
		         // replace by .directMode() for better performance
		         .consistencyLevel(ConsistencyLevel.SESSION)
		         .connectionSharingAcrossClientsEnabled(true)
		         .contentResponseOnWriteEnabled(true)
		         .buildClient();
		instance = new CosmosDBLayer( client);
		return instance;
	}
	public void putSession(Session s) {
		sessions.put(s.uid(), s);
	}
	
	public Session getSession(String uid) {
		return sessions.get(uid);
	}
	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer users_container;
	private CosmosContainer follows_container;
	private CosmosContainer likes_container;
	
	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init() {
		if( db != null)
			return;
		db = client.getDatabase(DB_NAME);
		users_container = db.getContainer(USERS_CONTAINER);
		follows_container = db.getContainer(FOLLOWS_CONTAINER);
		likes_container = db.getContainer(LIKES_CONTAINER);
		
	}

	public void close() {
		client.close();
	}

	private CosmosContainer getContainer(String type) {
		switch(type) {
			case("user"):
				return users_container;
			case("follow"):
				return follows_container;
			case("like"):
				return likes_container;
			default:
				return users_container;
		}
	}
	
	public <T> Result<T> getOne(String id, Class<T> clazz, String type) {
		return tryCatch( () -> getContainer(type).readItem(id, new PartitionKey(id), clazz).getItem());
	}
	
	public <T> Result<?> deleteOne(T obj, String type) {
		
			
		
		return tryCatch( () -> getContainer(type).deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<T> updateOne(T obj, String type) {
		return tryCatch( () -> getContainer(type).upsertItem(obj).getItem());
	}
	
	public <T> Result<T> insertOne( T obj, String type) {
		return tryCatch( () -> getContainer(type).createItem(obj).getItem());
	}
	
	public <T> Result<Stream<T>> query(Class<T> clazz, String queryStr, String type) {
		return tryCatch(() -> {
			var res = getContainer(type).queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream();
		});
	}
	
	<T> Result<T> tryCatch( Supplier<T> supplierFunc) {
		try {
			init();
			return Result.ok(supplierFunc.get());			
		} catch( CosmosException ce ) {
			ce.printStackTrace();
			return Result.error ( errorCodeFromStatus(ce.getStatusCode() ));		
		} catch( Exception x ) {
			x.printStackTrace();
			return Result.error( ErrorCode.INTERNAL_ERROR);						
		}
	}
	
	static Result.ErrorCode errorCodeFromStatus( int status ) {
		return switch( status ) {
		case 200 -> ErrorCode.OK;
		case 404 -> ErrorCode.NOT_FOUND;
		case 409 -> ErrorCode.CONFLICT;
		default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}
