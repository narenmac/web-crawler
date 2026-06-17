package com.webcrawler.fetcher.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import java.io.ByteArrayInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BlobStorageService {

    private final BlobContainerClient rawHtmlContainerClient;

    public BlobStorageService(@Value("${app.azure.storage.connection-string}") String storageConnectionString,
                              @Value("${app.azure.blobs.raw-html-container:raw-html}") String rawHtmlContainerName) {
        this.rawHtmlContainerClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(rawHtmlContainerName);
    }

    public String uploadRawHtml(String jobId, String urlHash, byte[] htmlBytes) {
        String blobPath = "%s/%s.html".formatted(jobId, urlHash);
        BlobClient blobClient = rawHtmlContainerClient.getBlobClient(blobPath);
        rawHtmlContainerClient.createIfNotExists();
        blobClient.upload(new ByteArrayInputStream(htmlBytes), htmlBytes.length, true);
        log.info("Uploaded raw HTML for job {} to {}", jobId, blobPath);
        return blobPath;
    }
}
