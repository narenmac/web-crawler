package com.webcrawler.orchestrator.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.webcrawler.orchestrator.model.JobRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class JobManager {

    private static final int MAX_RETRIES = 3;

    private final TableClient jobTableClient;
    private final BlobContainerClient seedContainerClient;
    private final QueuePublisher queuePublisher;
    private final DeduplicationService deduplicationService;

    public JobManager(@Value("${app.azure.storage.connection-string}") String storageConnectionString,
                      @Value("${app.azure.tables.jobs:jobs}") String jobsTableName,
                      @Value("${app.azure.blobs.seed-files-container:seed-files}") String seedFilesContainer,
                      QueuePublisher queuePublisher,
                      DeduplicationService deduplicationService) {
        this.jobTableClient = new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(jobsTableName)
                .buildClient();
        this.seedContainerClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(seedFilesContainer);
        this.queuePublisher = queuePublisher;
        this.deduplicationService = deduplicationService;
    }

    public synchronized JobRecord createJob(String userId, String seedFileUrl) {
        if (isJobRunning()) {
            throw new IllegalStateException("A crawl job is already running");
        }

        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        TableEntity entity = new TableEntity(userId, jobId)
                .addProperty("status", "RUNNING")
                .addProperty("currentLevel", 0)
                .addProperty("enqueuedUrlCount", 0)
                .addProperty("createdAt", now.toString())
                .addProperty("updatedAt", now.toString());
        if (StringUtils.hasText(seedFileUrl)) {
            entity.addProperty("seedFileUrl", seedFileUrl);
        }
        upsertJobEntity(entity);

        List<String> seedUrls = loadSeedUrls(seedFileUrl);
        int enqueued = 0;
        for (String seedUrl : seedUrls) {
            if (deduplicationService.markUrlVisited(jobId, seedUrl, 0, null)) {
                queuePublisher.publishToUrlQueue(jobId, seedUrl, 0, null);
                enqueued++;
            }
        }

        if (enqueued > 0) {
            incrementEnqueuedCount(jobId, enqueued);
        }

        log.info("Created job {} for user {} from seed file {} with {} seed URLs", jobId, userId, seedFileUrl, enqueued);
        return getJobStatus(jobId);
    }

    public synchronized JobRecord createJob(String userId, Collection<String> seedUrls, Integer maxDepth, Integer maxUrls) {
        if (isJobRunning()) {
            throw new IllegalStateException("A crawl job is already running");
        }

        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        TableEntity entity = new TableEntity(userId, jobId)
                .addProperty("status", "RUNNING")
                .addProperty("currentLevel", 0)
                .addProperty("enqueuedUrlCount", 0)
                .addProperty("createdAt", now.toString())
                .addProperty("updatedAt", now.toString());
        if (maxDepth != null) {
            entity.addProperty("maxDepth", maxDepth);
        }
        if (maxUrls != null) {
            entity.addProperty("maxUrls", maxUrls);
        }
        upsertJobEntity(entity);

        if (seedUrls != null) {
            int enqueued = 0;
            for (String seedUrl : seedUrls) {
                if (deduplicationService.markUrlVisited(jobId, seedUrl, 0, null)) {
                    queuePublisher.publishToUrlQueue(jobId, seedUrl, 0, null);
                    enqueued++;
                }
            }
            if (enqueued > 0) {
                incrementEnqueuedCount(jobId, enqueued);
            }
        }

        return getJobStatus(jobId);
    }

    public synchronized JobRecord startJob(String jobId) {
        return updateStatus(jobId, "RUNNING", null);
    }

    public synchronized JobRecord stopJob(String jobId) {
        queuePublisher.publishToJobControlQueue(jobId, "STOP");
        return updateStatus(jobId, "STOPPING", null);
    }

    public synchronized JobRecord completeJob(String jobId) {
        return updateStatus(jobId, "COMPLETED", Instant.now());
    }

    public JobRecord getJobStatus(String jobId) {
        return toJobRecord(getJobEntity(jobId));
    }

    public synchronized void incrementEnqueuedCount(String jobId, int increment) {
        if (increment <= 0) {
            return;
        }

        TableEntity entity = getJobEntity(jobId);
        int current = integerProperty(entity, "enqueuedUrlCount", 0);
        entity.addProperty("enqueuedUrlCount", current + increment);
        entity.addProperty("updatedAt", Instant.now().toString());
        upsertJobEntity(entity);
    }

    public boolean isJobRunning() {
        PagedIterable<TableEntity> entities = jobTableClient.listEntities(
                new ListEntitiesOptions().setFilter("status eq 'RUNNING'"),
                null,
                null);
        return entities.stream().findAny().isPresent();
    }

    public boolean hasRunningJob() {
        return isJobRunning();
    }

    public JobRecord getJob(String jobId) {
        return getJobStatus(jobId);
    }

    private JobRecord updateStatus(String jobId, String status, Instant completedAt) {
        TableEntity entity = getJobEntity(jobId);
        entity.addProperty("status", status);
        entity.addProperty("updatedAt", Instant.now().toString());
        if (completedAt != null) {
            entity.addProperty("completedAt", completedAt.toString());
        }
        upsertJobEntity(entity);
        return toJobRecord(entity);
    }

    private List<String> loadSeedUrls(String seedFileUrl) {
        if (!StringUtils.hasText(seedFileUrl)) {
            return List.of();
        }

        BlobClient blobClient = seedContainerClient.getBlobClient(resolveSeedBlobPath(seedFileUrl));
        String content = withRetry(() -> {
            seedContainerClient.createIfNotExists();
            return blobClient.downloadContent().toString();
        }, "download seed file");

        return content.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String resolveSeedBlobPath(String seedFileUrl) {
        String marker = "/" + seedContainerClient.getBlobContainerName() + "/";
        int markerIndex = seedFileUrl.indexOf(marker);
        if (markerIndex >= 0) {
            return seedFileUrl.substring(markerIndex + marker.length());
        }
        return seedFileUrl.replace("\\", "/").replaceFirst("^/+", "");
    }

    private TableEntity getJobEntity(String jobId) {
        Optional<TableEntity> entity = jobTableClient.listEntities(
                        new ListEntitiesOptions().setFilter("RowKey eq '%s'".formatted(escapeFilterValue(jobId))),
                        null,
                        null)
                .stream()
                .findFirst();
        return entity.orElseThrow(() -> new IllegalArgumentException("Unknown job id: " + jobId));
    }

    private JobRecord toJobRecord(TableEntity entity) {
        return JobRecord.builder()
                .id(entity.getRowKey())
                .userId(entity.getPartitionKey())
                .status(stringProperty(entity, "status"))
                .currentLevel(integerProperty(entity, "currentLevel", 0))
                .maxDepth(integerProperty(entity, "maxDepth", null))
                .maxUrls(integerProperty(entity, "maxUrls", null))
                .enqueuedUrlCount(integerProperty(entity, "enqueuedUrlCount", 0))
                .createdAt(instantProperty(entity, "createdAt"))
                .updatedAt(instantProperty(entity, "updatedAt"))
                .build();
    }

    private void upsertJobEntity(TableEntity entity) {
        withRetry(() -> {
            jobTableClient.upsertEntity(entity);
            return null;
        }, "upsert job entity");
    }

    private String stringProperty(TableEntity entity, String name) {
        Object value = entity.getProperty(name);
        return value == null ? null : value.toString();
    }

    private Integer integerProperty(TableEntity entity, String name, Integer defaultValue) {
        Object value = entity.getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Instant instantProperty(TableEntity entity, String name) {
        Object value = entity.getProperty(name);
        return value == null ? null : Instant.parse(value.toString());
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "''");
    }

    private <T> T withRetry(AzureOperation<T> operation, String action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.run();
            } catch (TableServiceException ex) {
                lastException = ex;
                log.warn("Azure operation failed while trying to {} (attempt {}/{}).", action, attempt, MAX_RETRIES, ex);
                sleep(attempt);
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("Operation failed while trying to {} (attempt {}/{}).", action, attempt, MAX_RETRIES, ex);
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

    @FunctionalInterface
    private interface AzureOperation<T> {
        T run();
    }
}
