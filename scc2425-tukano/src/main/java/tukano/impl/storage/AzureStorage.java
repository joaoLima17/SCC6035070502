package tukano.impl.storage;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;

import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import java.io.ByteArrayOutputStream;

import java.util.function.Consumer;

import com.azure.core.util.BinaryData;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import tukano.api.Result;

import utils.IO;

public class AzureStorage implements BlobStorage {

	private static final int CHUNK_SIZE = 4096;

	private static final String BLOBS_CONTAINER_NAME = "images";
	String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc70502;AccountKey=QaKYMH7zSblenT+ALAqyiyuzNBXQ03OEuv9Kv1C9J/z3WQcTj7wZqYbFlGsHbc2pqCtUo/oNLD+G+ASth1fEzA==;EndpointSuffix=core.windows.net";
	private static BlobContainerClient containerClient;

	public AzureStorage() {
		containerClient = new BlobContainerClientBuilder()
				.connectionString(storageConnectionString)
				.containerName(BLOBS_CONTAINER_NAME)
				.buildClient();

	}

	@Override
	public Result<Void> write(String path, byte[] bytes) {
		if (path == null)
			return error(BAD_REQUEST);
		BlobClient blob = containerClient.getBlobClient(path);
		if (blob == null)
			return error(NOT_FOUND);
		BinaryData x = BinaryData.fromBytes(bytes);
		blob.upload(x);
		return ok();
	}

	@Override
	public Result<byte[]> read(String path) {
		if (path == null)
			return error(BAD_REQUEST);

		BlobClient blob = containerClient.getBlobClient(path);
		if (blob == null)
			return error(NOT_FOUND);

		BinaryData x = blob.downloadContent();
		var bytes = x.toBytes();
		return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
	}

	@Override
	public Result<Void> read(String path, Consumer<byte[]> sink) {
		if (path == null)
			return error(BAD_REQUEST);

		BlobClient blob = containerClient.getBlobClient(path);
		if (blob == null)
			return error(NOT_FOUND);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(CHUNK_SIZE);
		blob.downloadStream(outputStream);
		IO.readstream(outputStream, CHUNK_SIZE, sink);
		return ok();
	}

	@Override
	public Result<Void> delete(String path) {
		BlobClient blob = containerClient.getBlobClient(path);
		if (blob == null)
			return error(NOT_FOUND);
		blob.delete();

		return ok();
	}
	
	@Override
	public Result<Void> deleteAll(String userId) {
		containerClient.listBlobs().forEach(blob -> {
			String[] path = blob.getName().split("/");
			if (path[0].equals(userId)) 
				delete(blob.getName());
		});
    	   
		return ok();
	}
}
