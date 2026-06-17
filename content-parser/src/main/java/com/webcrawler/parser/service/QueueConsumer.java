package com.webcrawler.parser.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.parser.model.ParseQueueMessage;
import com.webcrawler.parser.model.ResultQueueMessage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueueConsumer {

    private final BlobStorageService blobStorageService;
    private final LinkExtractor linkExtractor;
    private final ObjectMapper objectMapper;
    private final String resultQueueName;

    public QueueConsumer(BlobStorageService blobStorageService,
                         LinkExtractor linkExtractor,
                         ObjectMapper objectMapper,
                         @Value("${app.azure.queues.result-queue}") String resultQueueName) {
        this.blobStorageService = blobStorageService;
        this.linkExtractor = linkExtractor;
        this.objectMapper = objectMapper;
        this.resultQueueName = resultQueueName;
    }

    @Scheduled(fixedDelayString = "${app.polling.queue-ms:5000}")
    public void pollParseQueue() {
        // TODO: Receive messages from Azure Queue Storage and call processMessage for each parse task.
        log.debug("Polling parse queue for HTML parsing tasks");
    }

    public void processMessage(ParseQueueMessage message) {
        String html = blobStorageService.readRawHtml(message.getJobId(), message.getUrlHash());
        List<String> links = linkExtractor.extractLinks(message.getUrl(), html);

        ResultQueueMessage resultQueueMessage = ResultQueueMessage.builder()
                .jobId(message.getJobId())
                .sourceUrl(message.getUrl())
                .discoveredUrls(links)
                .level(message.getLevel())
                .build();

        publishResultMessage(resultQueueMessage);
    }

    private void publishResultMessage(ResultQueueMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            // TODO: Send payload to the Azure result-queue with QueueClient.sendMessage(payload).
            log.info("Publishing parser result to {}: {}", resultQueueName, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize parser result message", ex);
        }
    }
}
