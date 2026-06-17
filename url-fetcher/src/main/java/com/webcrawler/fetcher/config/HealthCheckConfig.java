package com.webcrawler.fetcher.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthIndicator azureStorageHealthIndicator(@Value("${app.azure.storage.connection-string}") String storageConnectionString,
                                                       @Value("${app.azure.blobs.raw-html-container:raw-html}") String rawHtmlContainer,
                                                       @Value("${app.azure.queues.url-queue}") String queueName) {
        BlobContainerClient blobContainerClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(rawHtmlContainer);
        QueueClient queueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(queueName)
                .buildClient();

        return () -> {
            try {
                boolean blobExists = blobContainerClient.exists();
                queueClient.getProperties();
                return Health.up()
                        .withDetail("blobContainer", rawHtmlContainer)
                        .withDetail("blobContainerExists", blobExists)
                        .withDetail("queueName", queueName)
                        .withDetail("queueReachable", true)
                        .build();
            } catch (RuntimeException ex) {
                return Health.down(ex)
                        .withDetail("blobContainer", rawHtmlContainer)
                        .withDetail("queueName", queueName)
                        .build();
            }
        };
    }
}
