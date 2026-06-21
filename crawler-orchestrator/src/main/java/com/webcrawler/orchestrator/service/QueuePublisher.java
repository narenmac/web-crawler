package com.webcrawler.orchestrator.service;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.orchestrator.model.JobControlMessage;
import com.webcrawler.orchestrator.model.UrlTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueuePublisher {

    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final QueueClient urlQueueClient;
    private final QueueClient jobControlQueueClient;
    private final String urlQueueName;
    private final String jobControlQueueName;

    public QueuePublisher(ObjectMapper objectMapper,
                          @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                          @Value("${app.azure.queues.url-queue}") String urlQueueName,
                          @Value("${app.azure.queues.job-control-queue}") String jobControlQueueName) {
        this.objectMapper = objectMapper;
        this.urlQueueName = urlQueueName;
        this.jobControlQueueName = jobControlQueueName;
        this.urlQueueClient = buildQueueClient(storageConnectionString, urlQueueName);
        this.jobControlQueueClient = buildQueueClient(storageConnectionString, jobControlQueueName);
    }

    public void publishToUrlQueue(String jobId, String url, int bfsLevel, String parentUrl) {
        UrlTaskMessage message = UrlTaskMessage.builder()
                .jobId(jobId)
                .url(url)
                .bfsLevel(bfsLevel)
                .parentUrl(parentUrl)
                .build();
        publish(urlQueueClient, urlQueueName, serialize(message));
        log.info("Enqueued URL to url-queue: {} (jobId={}, bfsLevel={}, parentUrl={})", url, jobId, bfsLevel, parentUrl);
    }

    public void publishToJobControlQueue(String jobId, String action) {
        JobControlMessage message = JobControlMessage.builder()
                .jobId(jobId)
                .action(action)
                .build();
        publish(jobControlQueueClient, jobControlQueueName, serialize(message));
    }

    public void publishUrlTask(UrlTaskMessage message) {
        publishToUrlQueue(message.getJobId(), message.getUrl(), message.getBfsLevel(), message.getParentUrl());
    }

    private void publish(QueueClient queueClient, String queueName, String payload) {
        withRetry(() -> {
            queueClient.createIfNotExists();
            queueClient.sendMessage(payload);
            log.info("Published message to {}", queueName);
            return null;
        }, "send queue message to " + queueName);
    }

    private QueueClient buildQueueClient(String connectionString, String queueName) {
        return new QueueClientBuilder()
                .connectionString(connectionString)
                .queueName(queueName)
                .buildClient();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize queue payload", ex);
        }
    }

    private <T> T withRetry(AzureOperation<T> operation, String action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.run();
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("Azure queue operation failed while trying to {} (attempt {}/{}).", action, attempt, MAX_RETRIES, ex);
                sleep(attempt);
            }
        }
        throw new IllegalStateException("Unable to " + action, lastException);
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Azure queue operation", ex);
        }
    }

    @FunctionalInterface
    private interface AzureOperation<T> {
        T run();
    }
}
