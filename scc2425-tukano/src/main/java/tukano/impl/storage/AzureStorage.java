package tukano.impl.storage;


import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;


import java.io.File;

import java.util.Arrays;

import java.util.function.Consumer;

import com.azure.core.util.BinaryData;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import tukano.api.Result;
import utils.Hash;
import utils.IO;

public class AzureStorage implements BlobStorage {
	private final String rootDir;
	private static final int CHUNK_SIZE = 4096;
	private static final String DEFAULT_ROOT_DIR = "/tmp/";
    private static final String BLOBS_CONTAINER_NAME = "images";
	String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc60350;AccountKey=PFFsaRsGU1GopDIjf1LAaFESIhI3emOS7/NHsfsAf1+KKOTqwqrNqRpv3mjkCof8+GQWU3xt4o6k+AStgeXKjA==;EndpointSuffix=core.windows.net";
    private static BlobContainerClient containerClient;

	public AzureStorage() {
       containerClient = new BlobContainerClientBuilder()
														.connectionString(storageConnectionString)
														.containerName(BLOBS_CONTAINER_NAME)
														.buildClient();
                                                        
		this.rootDir = DEFAULT_ROOT_DIR;
	}
	
	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);

		var file = toFile( path );

		if (file.exists()) {
			if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(IO.read(file))))
				return ok();
			else
				return error(CONFLICT);

		}
        
        BlobClient blob = containerClient.getBlobClient(path);
        BinaryData x = BinaryData.fromBytes(bytes);
        blob.upload(x);
		return ok();
	}

	@Override
	public Result<byte[]> read(String path) {
		if (path == null)
			return error(BAD_REQUEST);
		
		var file = toFile( path );
		if( ! file.exists() )
			return error(NOT_FOUND);
        BlobClient blob = containerClient.getBlobClient(path);
        BinaryData x = blob.downloadContent();
		var bytes = x.toBytes(); 
		return bytes != null ? ok( bytes ) : error( INTERNAL_ERROR );
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);
		
		var file = toFile( path );
		if( ! file.exists() )
			return error(NOT_FOUND);
		
		IO.read( file, CHUNK_SIZE, sink );
		return ok();
	}
	
	@Override
	public Result<Void> delete(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		
            BlobClient blob = containerClient.getBlobClient(path);
            blob.delete();
		
		return ok();
	}
	
	private File toFile(String path) {
		var res = new File( rootDir + path );
		
		var parent = res.getParentFile();
		if( ! parent.exists() )
			parent.mkdirs();
		
		return res;
	}

	
}