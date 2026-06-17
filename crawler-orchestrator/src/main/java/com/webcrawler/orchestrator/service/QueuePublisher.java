package com.webcrawler.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.orchestrator.model.ParseTaskMessage;
import com.webcrawler.orchestrator.model.UrlTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueuePublisher {

    private final ObjectMapper objectMapper;
    private final String storageConnectionString;
    private final String urlQueueName;
    private final String parseQueueName;

    public QueuePublisher(ObjectMapper objectMapper,
                          @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                          @Value("${app.azure.queues.url-queue}") String urlQueueName,
                          @Value("${app.azure.queues.parse-queue}") String parseQueueName) {
        this.objectMapper = objectMapper;
        this.storageConnectionString = storageConnectionString;
        this.urlQueueName = urlQueueName;
        this.parseQueueName = parseQueueName;
    }

    public void publishUrlTask(UrlTaskMessage message) {
        String payload = serialize(message);
        // TODO: Send payload to Azure Queue Storage using QueueClientBuilder and urlQueueName.
        log.info("Publishing URL task to {} using configured storage account: {}", urlQueueName, payload);
    }

    public void publishParseTask(ParseTaskMessage message) {
        String payload = serialize(message);
        // TODO: Send payload to Azure Queue Storage using QueueClientBuilder and parseQueueName.
        log.info("Publishing parse task to {} using configured storage account: {}", parseQueueName, payload);
    }

    public String getStorageConnectionString() {
        return storageConnectionString;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize queue payload", ex);
        }
    }
}
