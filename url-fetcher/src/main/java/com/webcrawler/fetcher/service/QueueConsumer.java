package com.webcrawler.fetcher.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.fetcher.model.ParseQueueMessage;
import com.webcrawler.fetcher.model.UrlQueueMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueueConsumer {

    private final FetchService fetchService;
    private final ContentHasher contentHasher;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;
    private final String parseQueueName;

    public QueueConsumer(FetchService fetchService,
                         ContentHasher contentHasher,
                         BlobStorageService blobStorageService,
                         ObjectMapper objectMapper,
                         @Value("${app.azure.queues.parse-queue}") String parseQueueName) {
        this.fetchService = fetchService;
        this.contentHasher = contentHasher;
        this.blobStorageService = blobStorageService;
        this.objectMapper = objectMapper;
        this.parseQueueName = parseQueueName;
    }

    @Scheduled(fixedDelayString = "${app.polling.queue-ms:5000}")
    public void pollUrlQueue() {
        // TODO: Receive messages from Azure Queue Storage and call processMessage for each URL task.
        log.debug("Polling URL queue for new fetch tasks");
    }

    public void processMessage(UrlQueueMessage message) {
        try {
            byte[] body = fetchService.fetch(message.getUrl());
            String urlHash = contentHasher.sha256(body);

            // TODO: Consult Azure Table Storage or Blob metadata for cross-node content deduplication.
            String blobPath = blobStorageService.uploadRawHtml(message.getJobId(), urlHash, body);

            ParseQueueMessage parseQueueMessage = ParseQueueMessage.builder()
                    .jobId(message.getJobId())
                    .url(message.getUrl())
                    .urlHash(urlHash)
                    .blobPath(blobPath)
                    .level(message.getLevel())
                    .build();

            publishParseMessage(parseQueueMessage);
        } catch (Exception ex) {
            log.error("Failed to process URL fetch task for {}", message.getUrl(), ex);
        }
    }

    private void publishParseMessage(ParseQueueMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            // TODO: Send payload to the Azure parse-queue with QueueClient.sendMessage(payload).
            log.info("Publishing parse task to {}: {}", parseQueueName, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize parse queue message", ex);
        }
    }
}
