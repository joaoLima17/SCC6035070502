package main.java.tukano.impl.rest.utils;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

public class DownloadFromStorage {
	private static final String BLOBS_CONTAINER_NAME = "images";

	public static void main(String[] args) {
		if( args.length != 1) {
			System.out.println( "Use: java scc.utils.DownloadFromStorage filename");
		}
		String filename = args[0];
		

		// Get connection string in the storage access keys page
		String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=tukano2425;AccountKey=W+ItR8zhwuliGXqntwaTDNfxI0MkEsSwPZVxaVpCfTC2SZo1RZQOmVoC9p95mebx6uz5Ja53Grcc+AStDriP4A==;EndpointSuffix=core.windows.net";

		try {
			// Get container client
			BlobContainerClient containerClient = new BlobContainerClientBuilder()
														.connectionString(storageConnectionString)
														.containerName(BLOBS_CONTAINER_NAME)
														.buildClient();

			// Get client to blob
			BlobClient blob = containerClient.getBlobClient( filename);

			// Download contents to BinaryData (check documentation for other alternatives)
			BinaryData data = blob.downloadContent();
			
			byte[] arr = data.toBytes();
			
			System.out.println( "Blob size : " + arr.length);
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
}