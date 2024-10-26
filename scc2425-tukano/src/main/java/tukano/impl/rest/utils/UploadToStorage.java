package main.java.tukano.impl.rest.utils;

import java.nio.file.Path;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;


public class UploadToStorage {

	private static final String BLOBS_CONTAINER_NAME = "images";

	public static void main(String[] args) {
		if( args.length != 1) {
			System.out.println( "Use: java scc.utils.UploadToStorage filename");
			System.exit(0);
		}
		String filename = args[0];
		

		// Get connection string in the storage access keys page
		String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=tukano2425;AccountKey=W+ItR8zhwuliGXqntwaTDNfxI0MkEsSwPZVxaVpCfTC2SZo1RZQOmVoC9p95mebx6uz5Ja53Grcc+AStDriP4A==;EndpointSuffix=core.windows.net";

		try {
			BinaryData data = BinaryData.fromFile(Path.of(filename));

			// Get container client
			BlobContainerClient containerClient = new BlobContainerClientBuilder()
														.connectionString(storageConnectionString)
														.containerName(BLOBS_CONTAINER_NAME)
														.buildClient();

			// Get client to blob
			BlobClient blob = containerClient.getBlobClient( filename);

			// Upload contents from BinaryData (check documentation for other alternatives)
			blob.upload(data);
			
			System.out.println( "File uploaded : " + filename);
			
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
}