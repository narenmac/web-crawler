package com.webcrawler.fetcher.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BlobStorageService {

    private final String storageConnectionString;

    public BlobStorageService(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    public String uploadRawHtml(String jobId, String urlHash, byte[] htmlBytes) {
        String blobPath = "raw-html/%s/%s.html".formatted(jobId, urlHash);
        BlobClient blobClient = containerClient().getBlobClient(blobPath);

        // TODO: Replace the placeholder upload with blobClient.upload(new ByteArrayInputStream(htmlBytes), htmlBytes.length, true).
        new ByteArrayInputStream(htmlBytes);
        blobClient.getBlobUrl();
        return blobPath;
    }

    private BlobContainerClient containerClient() {
        return new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient("crawler-content");
    }
}
