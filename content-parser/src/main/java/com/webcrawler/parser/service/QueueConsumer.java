package com.webcrawler.parser.service;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.parser.model.ParseQueueMessage;
import com.webcrawler.parser.model.ResultQueueMessage;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class QueueConsumer {

    private static final int MAX_RETRIES = 3;

    private final BlobStorageService blobStorageService;
    private final LinkExtractor linkExtractor;
    private final ObjectMapper objectMapper;
    private final QueueClient parseQueueClient;
    private final QueueClient resultQueueClient;
    private final QueueClient poisonQueueClient;
    private final String resultQueueName;
    private final String poisonQueueName;
    private final int maxDequeueCount;
    private final Duration shutdownWaitTimeout;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicInteger inFlightMessages = new AtomicInteger(0);

    public QueueConsumer(BlobStorageService blobStorageService,
                         LinkExtractor linkExtractor,
                         ObjectMapper objectMapper,
                         @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                         @Value("${app.azure.queues.parse-queue}") String parseQueueName,
                         @Value("${app.azure.queues.result-queue}") String resultQueueName,
                         @Value("${app.azure.queues.parse-poison-queue:parse-queue-poison}") String poisonQueueName,
                         @Value("${app.processing.max-dequeue-count:5}") int maxDequeueCount,
                         @Value("${app.shutdown.wait-timeout:PT30S}") Duration shutdownWaitTimeout) {
        this.blobStorageService = blobStorageService;
        this.linkExtractor = linkExtractor;
        this.objectMapper = objectMapper;
        this.resultQueueName = resultQueueName;
        this.poisonQueueName = poisonQueueName;
        this.maxDequeueCount = maxDequeueCount;
        this.shutdownWaitTimeout = shutdownWaitTimeout;
        this.parseQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(parseQueueName)
                .buildClient();
        this.resultQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(resultQueueName)
                .buildClient();
        this.poisonQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(poisonQueueName)
                .buildClient();
    }

    @Scheduled(fixedDelayString = "${app.polling.queue-ms:1000}")
    public void pollParseQueue() {
        if (shutdownRequested.get()) {
            return;
        }

        parseQueueClient.createIfNotExists();
        resultQueueClient.createIfNotExists();
        poisonQueueClient.createIfNotExists();

        for (QueueMessageItem messageItem : parseQueueClient.receiveMessages(32, Duration.ofSeconds(30), null, Context.NONE)) {
            if (shutdownRequested.get()) {
                break;
            }

            inFlightMessages.incrementAndGet();
            try {
                if (messageItem.getDequeueCount() > maxDequeueCount) {
                    deadLetterMessage(messageItem, "Exceeded dequeue threshold");
                    continue;
                }

                ParseQueueMessage message = objectMapper.readValue(messageItem.getMessageText(), ParseQueueMessage.class);
                setupLoggingContext(messageItem, message);
                processMessage(message);
                parseQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
            } catch (JsonProcessingException ex) {
                log.error("Invalid parse queue payload {}", messageItem.getMessageId(), ex);
                deadLetterMessage(messageItem, "Invalid queue payload");
            } catch (Exception ex) {
                log.error("Failed to process parse queue message {}", messageItem.getMessageId(), ex);
            } finally {
                clearLoggingContext();
                inFlightMessages.decrementAndGet();
            }
        }
    }

    public void processMessage(ParseQueueMessage message) {
        String html = blobStorageService.readRawHtml(message.getBlobPath());
        List<String> links = Collections.emptyList();

        if (!StringUtils.hasText(html)) {
            log.warn("Raw HTML for {} was empty; publishing empty result set", message.getUrl());
        } else {
            try {
                links = linkExtractor.extractLinks(html, message.getUrl());
            } catch (RuntimeException ex) {
                log.warn("Unable to parse HTML for {}; continuing with empty links", message.getUrl(), ex);
            }
        }

        ResultQueueMessage resultQueueMessage = ResultQueueMessage.builder()
                .jobId(message.getJobId())
                .parentUrl(message.getUrl())
                .discoveredLinks(links)
                .parentBfsLevel(message.getBfsLevel())
                .build();

        publishResultMessage(resultQueueMessage);
    }

    private void publishResultMessage(ResultQueueMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            withRetry(() -> {
                resultQueueClient.sendMessage(payload);
                return null;
            }, "send parser result message");
            log.info("Publishing parser result to {} for {}", resultQueueName, message.getParentUrl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize parser result message", ex);
        }
    }

    private void deadLetterMessage(QueueMessageItem messageItem, String reason) {
        withRetry(() -> {
            poisonQueueClient.sendMessage(messageItem.getMessageText());
            return null;
        }, "move parse message to poison queue");
        parseQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
        log.warn("Moved parse queue message {} to poison queue {} because {}", messageItem.getMessageId(), poisonQueueName, reason);
    }

    private void setupLoggingContext(QueueMessageItem messageItem, ParseQueueMessage message) {
        MDC.put("correlationId", messageItem.getMessageId());
        if (message.getJobId() != null && !message.getJobId().isBlank()) {
            MDC.put("jobId", message.getJobId());
        }
    }

    private void clearLoggingContext() {
        MDC.remove("jobId");
        MDC.remove("correlationId");
    }

    private <T> T withRetry(AzureOperation<T> operation, String action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.run();
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("Azure operation failed while trying to {} (attempt {}/{}).", action, attempt, MAX_RETRIES, ex);
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
            throw new IllegalStateException("Interrupted while retrying Azure operation", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdownRequested.set(true);
        Instant deadline = Instant.now().plus(shutdownWaitTimeout);
        while (inFlightMessages.get() > 0 && Instant.now().isBefore(deadline)) {
            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Parse queue consumer stopped with {} in-flight messages remaining", inFlightMessages.get());
    }

    @FunctionalInterface
    private interface AzureOperation<T> {
        T run();
    }
}
