package com.webcrawler.fetcher.service;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.fetcher.model.ParseQueueMessage;
import com.webcrawler.fetcher.model.UrlQueueMessage;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueueConsumer {

    private static final int MAX_RETRIES = 3;

    private final FetchService fetchService;
    private final RobotsTxtService robotsTxtService;
    private final PolitenessService politenessService;
    private final ContentHasher contentHasher;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;
    private final QueueClient urlQueueClient;
    private final QueueClient parseQueueClient;
    private final QueueClient poisonQueueClient;
    private final TableClient urlMetadataTableClient;
    private final TableClient contentHashesTableClient;
    private final String parseQueueName;
    private final String poisonQueueName;
    private final int maxDequeueCount;
    private final Duration shutdownWaitTimeout;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final AtomicInteger inFlightMessages = new AtomicInteger(0);

    public QueueConsumer(FetchService fetchService,
                         RobotsTxtService robotsTxtService,
                         PolitenessService politenessService,
                         ContentHasher contentHasher,
                         BlobStorageService blobStorageService,
                         ObjectMapper objectMapper,
                         @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                         @Value("${app.azure.queues.url-queue}") String urlQueueName,
                         @Value("${app.azure.queues.parse-queue}") String parseQueueName,
                         @Value("${app.azure.queues.url-poison-queue:url-queue-poison}") String poisonQueueName,
                         @Value("${app.azure.tables.urlmetadata:urlmetadata}") String urlMetadataTableName,
                         @Value("${app.azure.tables.contenthashes:contenthashes}") String contentHashesTableName,
                         @Value("${app.processing.max-dequeue-count:5}") int maxDequeueCount,
                         @Value("${app.shutdown.wait-timeout:PT30S}") Duration shutdownWaitTimeout) {
        this.fetchService = fetchService;
        this.robotsTxtService = robotsTxtService;
        this.politenessService = politenessService;
        this.contentHasher = contentHasher;
        this.blobStorageService = blobStorageService;
        this.objectMapper = objectMapper;
        this.parseQueueName = parseQueueName;
        this.poisonQueueName = poisonQueueName;
        this.maxDequeueCount = maxDequeueCount;
        this.shutdownWaitTimeout = shutdownWaitTimeout;
        this.urlQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(urlQueueName)
                .buildClient();
        this.parseQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(parseQueueName)
                .buildClient();
        this.poisonQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(poisonQueueName)
                .buildClient();
        this.urlMetadataTableClient = new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(urlMetadataTableName)
                .buildClient();
        this.contentHashesTableClient = new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(contentHashesTableName)
                .buildClient();
    }

    @Scheduled(fixedDelayString = "${app.polling.queue-ms:1000}")
    public void pollUrlQueue() {
        if (shutdownRequested.get()) {
            return;
        }

        urlQueueClient.createIfNotExists();
        parseQueueClient.createIfNotExists();
        poisonQueueClient.createIfNotExists();

        // 5-minute visibility timeout prevents duplicate processing when fetch + upload takes longer than default 30s
        for (QueueMessageItem messageItem : urlQueueClient.receiveMessages(32, Duration.ofSeconds(300), null, Context.NONE)) {
            if (shutdownRequested.get()) {
                break;
            }

            inFlightMessages.incrementAndGet();
            try {
                if (messageItem.getDequeueCount() > maxDequeueCount) {
                    deadLetterMessage(messageItem, "Exceeded dequeue threshold");
                    continue;
                }

                ProcessingOutcome outcome = processMessage(messageItem);
                if (outcome.deleteOriginalMessage()) {
                    urlQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
                }
            } catch (JsonProcessingException ex) {
                log.error("Invalid URL queue payload {}", messageItem.getMessageId(), ex);
                deadLetterMessage(messageItem, "Invalid queue payload");
            } catch (Exception ex) {
                log.error("Failed to process URL queue message {}", messageItem.getMessageId(), ex);
            } finally {
                clearLoggingContext();
                inFlightMessages.decrementAndGet();
            }
        }
    }

    private ProcessingOutcome processMessage(QueueMessageItem messageItem) throws JsonProcessingException {
        setupLoggingContext(messageItem);
        UrlQueueMessage message = objectMapper.readValue(messageItem.getMessageText(), UrlQueueMessage.class);
        if (message.getJobId() != null && !message.getJobId().isBlank()) {
            MDC.put("jobId", message.getJobId());
        }

        String urlHash = computeUrlHash(message.getUrl());
        URI targetUri = parseUri(message.getUrl(), urlHash, message);
        if (targetUri == null) {
            return ProcessingOutcome.DELETE;
        }

        RobotsTxtService.RobotsTxtRules robotsRules = robotsTxtService.getRules(targetUri);
        if (!robotsTxtService.isAllowed(targetUri, robotsRules)) {
            updateUrlMetadata(message, urlHash, "SKIPPED_ROBOTS", null, null, null, "Blocked by robots.txt");
            log.info("Skipping {} because robots.txt disallows crawling", message.getUrl());
            return ProcessingOutcome.DELETE;
        }

        Duration remainingDelay = politenessService.remainingDelay(targetUri, robotsRules);
        if (!remainingDelay.isZero() && !remainingDelay.isNegative()) {
            requeueWithDelay(messageItem.getMessageText(), remainingDelay);
            log.info("Re-queued {} for politeness delay of {} ms", message.getUrl(), remainingDelay.toMillis());
            return ProcessingOutcome.DELETE;
        }

        try {
            FetchResult fetchResult = fetchService.fetch(message.getUrl());
            politenessService.recordFetch(targetUri);
            String contentHash = contentHasher.computeHash(fetchResult.content());

            if (isContentDuplicate(message.getJobId(), contentHash)) {
                updateUrlMetadata(message, urlHash, "SKIPPED_DUPLICATE", contentHash, fetchResult.statusCode(), fetchResult.contentType(), null);
                log.info("Skipped duplicate content for {}", message.getUrl());
                return ProcessingOutcome.DELETE;
            }

            String blobPath = blobStorageService.uploadRawHtml(message.getJobId(), urlHash, fetchResult.content());
            storeContentHash(message, urlHash, contentHash, fetchResult);
            updateUrlMetadata(message, urlHash, "COMPLETED", contentHash, fetchResult.statusCode(), fetchResult.contentType(), null);

            ParseQueueMessage parseQueueMessage = ParseQueueMessage.builder()
                    .jobId(message.getJobId())
                    .url(message.getUrl())
                    .urlHash(urlHash)
                    .blobPath(blobPath)
                    .bfsLevel(message.getBfsLevel())
                    .parentUrl(message.getParentUrl())
                    .build();
            publishParseMessage(parseQueueMessage);
            return ProcessingOutcome.DELETE;
        } catch (FetchService.FetchFailedException ex) {
            politenessService.recordFetch(targetUri);
            updateUrlMetadata(message, urlHash, "FAILED", null, ex.getStatusCode(), null, ex.getMessage());
            log.warn("Fetch failed for {}", message.getUrl(), ex);
            return ProcessingOutcome.DELETE;
        }
    }

    private URI parseUri(String url, String urlHash, UrlQueueMessage message) {
        try {
            return new URI(url.trim()).normalize();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            updateUrlMetadata(message, urlHash, "FAILED", null, null, null, "Invalid URL syntax");
            log.warn("Invalid URL {}", url, ex);
            return null;
        }
    }

    private boolean isContentDuplicate(String jobId, String contentHash) {
        try {
            contentHashesTableClient.getEntity(jobId, contentHash);
            return true;
        } catch (TableServiceException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void storeContentHash(UrlQueueMessage message, String urlHash, String contentHash, FetchResult fetchResult) {
        TableEntity entity = new TableEntity(message.getJobId(), contentHash)
                .addProperty("url", message.getUrl())
                .addProperty("urlHash", urlHash)
                .addProperty("statusCode", fetchResult.statusCode())
                .addProperty("contentType", fetchResult.contentType())
                .addProperty("createdAt", Instant.now().toString());
        withRetry(() -> {
            contentHashesTableClient.upsertEntity(entity);
            return null;
        }, "upsert content hash entity");
    }

    private void updateUrlMetadata(UrlQueueMessage message,
                                   String urlHash,
                                   String status,
                                   String contentHash,
                                   Integer statusCode,
                                   String contentType,
                                   String errorMessage) {
        TableEntity entity = new TableEntity(message.getJobId(), urlHash)
                .addProperty("url", message.getUrl())
                .addProperty("bfsLevel", message.getBfsLevel())
                .addProperty("status", status)
                .addProperty("updatedAt", Instant.now().toString());
        if (message.getParentUrl() != null && !message.getParentUrl().isBlank()) {
            entity.addProperty("parentUrl", message.getParentUrl());
        }
        if (contentHash != null) {
            entity.addProperty("contentHash", contentHash);
        }
        if (statusCode != null) {
            entity.addProperty("statusCode", statusCode);
        }
        if (contentType != null) {
            entity.addProperty("contentType", contentType);
        }
        if (errorMessage != null) {
            entity.addProperty("errorMessage", errorMessage);
        }
        withRetry(() -> {
            urlMetadataTableClient.upsertEntity(entity);
            return null;
        }, "upsert url metadata entity");
    }

    private void publishParseMessage(ParseQueueMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            withRetry(() -> {
                parseQueueClient.sendMessage(payload);
                return null;
            }, "send parse queue message");
            log.info("Publishing parse task to {} for {}", parseQueueName, message.getUrl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize parse queue message", ex);
        }
    }

    private void requeueWithDelay(String payload, Duration delay) {
        long visibilitySeconds = Math.max(1L, (long) Math.ceil(delay.toMillis() / 1000.0));
        withRetry(() -> {
            urlQueueClient.sendMessageWithResponse(
                    BinaryData.fromString(payload),
                    Duration.ofSeconds(visibilitySeconds),
                    Duration.ofSeconds(-1),
                    null,
                    Context.NONE);
            return null;
        }, "requeue delayed URL message");
    }

    private void deadLetterMessage(QueueMessageItem messageItem, String reason) {
        withRetry(() -> {
            poisonQueueClient.sendMessage(messageItem.getMessageText());
            return null;
        }, "move URL message to poison queue");
        urlQueueClient.deleteMessage(messageItem.getMessageId(), messageItem.getPopReceipt());
        log.warn("Moved URL queue message {} to poison queue {} because {}", messageItem.getMessageId(), poisonQueueName, reason);
    }

    private String computeUrlHash(String url) {
        try {
            URI uri = new URI(url.trim()).normalize();
            URI normalized = new URI(
                    uri.getScheme() == null ? null : uri.getScheme().toLowerCase(),
                    uri.getUserInfo(),
                    uri.getHost() == null ? null : uri.getHost().toLowerCase(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null);
            return contentHasher.computeHash(normalized.toString());
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return contentHasher.computeHash(url.trim());
        }
    }

    private void setupLoggingContext(QueueMessageItem messageItem) {
        MDC.put("correlationId", messageItem.getMessageId());
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
        log.info("URL queue consumer stopped with {} in-flight messages remaining", inFlightMessages.get());
    }

    @FunctionalInterface
    private interface AzureOperation<T> {
        T run();
    }

    private enum ProcessingOutcome {
        DELETE(true);

        private final boolean deleteOriginalMessage;

        ProcessingOutcome(boolean deleteOriginalMessage) {
            this.deleteOriginalMessage = deleteOriginalMessage;
        }

        private boolean deleteOriginalMessage() {
            return deleteOriginalMessage;
        }
    }
}
