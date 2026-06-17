package com.webcrawler.parser.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BlobStorageService {

    private final String storageConnectionString;

    public BlobStorageService(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    public String readRawHtml(String jobId, String urlHash) {
        BlobClient blobClient = containerClient().getBlobClient("raw-html/%s/%s.html".formatted(jobId, urlHash));

        // TODO: Replace the placeholder return value with blobClient.downloadContent().toString().
        return "<html><body><a href=\"/next\">Next</a></body></html><!-- " + blobClient.getBlobUrl() + " -->";
    }

    private BlobContainerClient containerClient() {
        return new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient("crawler-content");
    }
}
