package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.logging.Logger;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import tukano.api.Authentication;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Session;
import tukano.impl.rest.TukanoRestServer;
//import tukano.impl.storage.AzureStorage;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.Hash;
import utils.Hex;


public class JavaBlobs implements Blobs {
	
	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	public String baseURI;
	private BlobStorage storage;
	
	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}
	
	private JavaBlobs() {
		String localBasePath = "/mnt/data/tukano";
        storage = new FilesystemStorage(localBasePath);
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}
	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		/*
		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);
		*/
		
		String id = blobId.split("\\+")[0];

		try {
			Authentication.validateSession(id);
		} catch (Exception e) {
			return error(BAD_REQUEST);
		}

		return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));
		
		/*
		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);
		*/
		String id = blobId.split("\\+")[0];
		try {
			Authentication.validateSession(id);
		} catch (Exception e) {
			return error(BAD_REQUEST);
		}

		return storage.read( toPath( blobId ) );
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));
		try {
			Authentication.validateSession("admin");
		} catch (Exception e) {
			return error(BAD_REQUEST);
		}
		
		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);
		

		return storage.delete( toPath(blobId));
	}
	
	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
				
		return storage.deleteAll(userId);
	}
	
	private boolean validBlobId(String blobId, String token) {		
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
}