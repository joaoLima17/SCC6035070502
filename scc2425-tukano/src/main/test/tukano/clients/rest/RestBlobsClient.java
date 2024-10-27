package main.test.tukano.clients.rest;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import main.java.tukano.api.Blobs;
import main.java.tukano.api.Result;
import main.java.tukano.api.rest.RestBlobs;

import java.nio.file.Path;
//nao testado
public class RestBlobsClient extends RestClient implements Blobs {
	String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc60350;AccountKey=PFFsaRsGU1GopDIjf1LAaFESIhI3emOS7/NHsfsAf1+KKOTqwqrNqRpv3mjkCof8+GQWU3xt4o6k+AStgeXKjA==;EndpointSuffix=core.windows.net";
	private static final String BLOBS_CONTAINER_NAME = "images";
	BlobContainerClient containerClient;
	public RestBlobsClient(String serverURI) {
		super(serverURI, RestBlobs.PATH);
		containerClient = new BlobContainerClientBuilder()
				.connectionString(storageConnectionString)
				.containerName(BLOBS_CONTAINER_NAME)
				.buildClient();
	}

	private Result<Void> _upload(String blobURL, byte[] bytes, String token) {
		BlobClient blob;
		BinaryData data;
		try {
			data = BinaryData.fromBytes(bytes);
			blob =containerClient.getBlobClient( blobURL);
			blob.upload(data);
		} catch (Exception e) {
            throw new RuntimeException(e);
        }

        return super.toJavaResult(
				client.target( blobURL )
				.queryParam(RestBlobs.TOKEN, token)
				.request()
				.post( Entity.entity(bytes, MediaType.APPLICATION_OCTET_STREAM_TYPE))
		);
	}

	private Result<byte[]> _download(String blobURL, String token) {
		BlobClient blob;
		BinaryData data;
		try {
			blob = containerClient.getBlobClient( blobURL);
			// Download contents to BinaryData (check documentation for other alternatives)
			data = blob.downloadContent();

			byte[] arr = data.toBytes();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return super.toJavaResult(
				client.target( blobURL )
				.queryParam(RestBlobs.TOKEN, token)
				.request()
				.accept(MediaType.APPLICATION_OCTET_STREAM_TYPE)
				.get(), byte[].class);
	}
// TODO delete blobs com azure
	private Result<Void> _delete(String blobURL, String token) {

		return super.toJavaResult(
				client.target( blobURL )
				.queryParam( RestBlobs.TOKEN, token )
				.request()
				.delete());
	}
	
	private Result<Void> _deleteAllBlobs(String userId, String token) {
		return super.toJavaResult(
				target.path(userId)
				.path(RestBlobs.BLOBS)
				.queryParam( RestBlobs.TOKEN, token )
				.request()
				.delete());
	}
	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		return super.reTry( () -> _upload(blobId, bytes, token));
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		return super.reTry( () -> _download(blobId, token));
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		return super.reTry( () -> _delete(blobId, token));
	}
	
	@Override
	public Result<Void> deleteAllBlobs(String userId, String password) {
		return super.reTry( () -> _deleteAllBlobs(userId, password));
	}
}
