package com.webcrawler.parser.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
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

    public String readRawHtml(String blobPath) {
        BlobClient blobClient = rawHtmlContainerClient.getBlobClient(blobPath);
        String html = blobClient.downloadContent().toString();
        log.info("Downloaded raw HTML from {}", blobPath);
        return html;
    }
}
