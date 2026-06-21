package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.model.Job;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_STOP_REQUESTED = "STOP_REQUESTED";

    @Value("${app.azure.storage.connection-string}")
    private String storageConnectionString;

    @Value("${app.azure.storage.seed-container:crawler-input}")
    private String seedContainerName;

    @Value("${app.azure.storage.content-container:crawler-content}")
    private String contentContainerName;

    @Value("${app.azure.queues.job-control-queue:job-control-queue}")
    private String jobControlQueueName;

    @Value("${app.orchestrator.start-endpoint:http://localhost:8081/internal/jobs/start}")
    private String orchestratorStartEndpoint;

    @Value("${app.crawl.max-urls:100}")
    private int defaultMaxUrls;

    private final ObjectMapper objectMapper;

    @PostConstruct
    void initStorage() {
        log.info("Initializing storage tables, queues, and containers...");
        try {
            getOrCreateTable("jobs");
            getOrCreateTable("urlmetadata");
            getOrCreateQueue(jobControlQueueName);
            getOrCreateContainer(seedContainerName);
            getOrCreateContainer(contentContainerName);
            log.info("Storage initialization complete.");
        } catch (Exception e) {
            log.warn("Storage initialization failed (will retry on first use): {}", e.getMessage());
        }
    }

    public JobResponse createJob(String userId, MultipartFile seedFile) {
        ensureNoRunningJobs();

        String jobId = UUID.randomUUID().toString();
        String source = "seed-files/%s/%s.txt".formatted(userId, jobId);
        uploadSeedFile(source, seedFile);

        Job job = newJob(userId, jobId, source);
        getJobTableClient().upsertEntity(job.toEntity());

        try {
            sendStartSignal(job);
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch start signal for job {}", jobId, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to start crawl job", ex);
        }

        log.info("Created crawl job {} for user {}", jobId, userId);
        return toResponse(job);
    }

    public JobResponse createJobFromSeedSource(String userId, String seedFilePath) {
        if (!StringUtils.hasText(seedFilePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file reference is required");
        }
        if (!seedBlobContainerClient().getBlobClient(seedFilePath).exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referenced seed file was not found");
        }

        ensureNoRunningJobs();

        String jobId = UUID.randomUUID().toString();
        Job job = newJob(userId, jobId, seedFilePath);
        getJobTableClient().upsertEntity(job.toEntity());
        sendStartSignal(job);

        log.info("Created crawl job {} from existing seed source {} for user {}", jobId, seedFilePath, userId);
        return toResponse(job);
    }

    public List<JobResponse> getJobs(String userId) {
        String filter = "PartitionKey eq '%s'".formatted(escapeOData(userId));
        return StreamSupport.stream(getJobTableClient()
                        .listEntities(new ListEntitiesOptions().setFilter(filter), null, null)
                        .spliterator(), false)
                .map(Job::fromEntity)
                .sorted(Comparator.comparing(Job::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public JobResponse getJob(String userId, String jobId) {
        return toResponse(findOwnedJob(userId, jobId));
    }

    public JobResponse stopJob(String userId, String jobId) {
        Job job = findOwnedJob(userId, jobId);
        String status = job.getStatus();
        if (!STATUS_RUNNING.equalsIgnoreCase(status) && !STATUS_PENDING.equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only running or pending jobs can be stopped");
        }

        job.setStatus(STATUS_STOP_REQUESTED);
        getJobTableClient().upsertEntity(job.toEntity());
        sendControlMessage(Map.of("jobId", jobId, "action", "STOP", "userId", userId));

        log.info("Queued stop request for job {}", jobId);
        return toResponse(job);
    }

    public void deleteJob(String userId, String jobId) {
        Job job = findOwnedJob(userId, jobId);

        var metadataPages = getUrlMetadataTableClient()
                .listEntities(new ListEntitiesOptions().setFilter("PartitionKey eq '%s'".formatted(escapeOData(jobId))), null, null)
                .iterableByPage();
        List<TableEntity> metadataRows = StreamSupport.stream(metadataPages.spliterator(), false)
                .flatMap(page -> StreamSupport.stream(page.getElements().spliterator(), false))
                .toList();

        metadataRows.forEach(entity -> {
            Object blobPath = entity.getProperty("blobPath");
            if (blobPath instanceof String path && StringUtils.hasText(path)) {
                rawHtmlContainerClient().getBlobClient(path).deleteIfExists();
            }
            getUrlMetadataTableClient().deleteEntity(entity.getPartitionKey(), entity.getRowKey());
        });

        if (StringUtils.hasText(job.getSource()) && job.getSource().startsWith("seed-files/%s/%s".formatted(userId, jobId))) {
            seedBlobContainerClient().getBlobClient(job.getSource()).deleteIfExists();
        }

        getJobTableClient().deleteEntity(userId, jobId);
        log.info("Deleted job {} and {} result rows", jobId, metadataRows.size());
    }

    private Job findOwnedJob(String userId, String jobId) {
        try {
            return Job.fromEntity(getJobTableClient().getEntity(userId, jobId));
        } catch (TableServiceException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
            }
            throw ex;
        }
    }

    private void ensureNoRunningJobs() {
        var runningPages = getJobTableClient()
                .listEntities(new ListEntitiesOptions().setFilter("status eq '%s'".formatted(STATUS_RUNNING)), null, null)
                .iterableByPage();
        boolean runningJobPresent = StreamSupport.stream(runningPages.spliterator(), false)
                .flatMap(page -> StreamSupport.stream(page.getElements().spliterator(), false))
                .findAny()
                .isPresent();
        if (runningJobPresent) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A crawl job is already running");
        }
    }

    private Job newJob(String userId, String jobId, String source) {
        Instant now = Instant.now();
        return Job.builder()
                .partitionKey(userId)
                .rowKey(jobId)
                .status(STATUS_PENDING)
                .totalUrls(0)
                .crawledUrls(0)
                .currentBfsLevel(0)
                .maxUrls(defaultMaxUrls)
                .createdAt(now)
                .completedAt(null)
                .source(source)
                .build();
    }

    private void uploadSeedFile(String blobPath, MultipartFile seedFile) {
        try {
            BlobContainerClient containerClient = seedBlobContainerClient();
            containerClient.createIfNotExists();
            containerClient.getBlobClient(blobPath).upload(seedFile.getInputStream(), seedFile.getSize(), true);
        } catch (IOException | BlobStorageException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to upload seed file", ex);
        }
    }

    private void sendStartSignal(Job job) {
        log.info("Sending start signal to orchestrator: jobId={}, seedFile={}, maxUrls={}, endpoint={}",
                job.getRowKey(), job.getSource(), job.getMaxUrls(), orchestratorStartEndpoint);
        Map<String, Object> payload = Map.of(
                "jobId", job.getRowKey(),
                "userId", job.getPartitionKey(),
                "seedFileUrl", job.getSource(),
                "status", job.getStatus(),
                "maxUrls", job.getMaxUrls()
        );
        RestClient.create()
                .post()
                .uri(orchestratorStartEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private void sendControlMessage(Map<String, Object> payload) {
        try {
            QueueClient queueClient = new QueueClientBuilder()
                    .connectionString(storageConnectionString)
                    .queueName(jobControlQueueName)
                    .buildClient();
            queueClient.createIfNotExists();
            queueClient.sendMessage(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize control message", ex);
        }
    }

    private JobResponse toResponse(Job job) {
        int crawledCount = countCrawledUrls(job.getRowKey());
        return JobResponse.builder()
                .id(job.getRowKey())
                .status(job.getStatus())
                .totalUrls(job.getTotalUrls())
                .crawledUrls(crawledCount)
                .currentBfsLevel(job.getCurrentBfsLevel())
                .maxUrls(job.getMaxUrls())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .source(job.getSource())
                .build();
    }

    private int countCrawledUrls(String jobId) {
        if (jobId == null) {
            return 0;
        }
        try {
            String filter = "PartitionKey eq '%s'".formatted(escapeOData(jobId));
            return (int) StreamSupport.stream(
                    getUrlMetadataTableClient()
                            .listEntities(new ListEntitiesOptions().setFilter(filter), null, null)
                            .spliterator(), false)
                    .count();
        } catch (Exception e) {
            log.debug("Failed to count crawled URLs for job {}: {}", jobId, e.getMessage());
            return 0;
        }
    }

    private TableClient getJobTableClient() {
        return getOrCreateTable("jobs");
    }

    private TableClient getUrlMetadataTableClient() {
        return getOrCreateTable("urlmetadata");
    }

    private BlobContainerClient seedBlobContainerClient() {
        BlobContainerClient client = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(seedContainerName);
        client.createIfNotExists();
        return client;
    }

    private BlobContainerClient rawHtmlContainerClient() {
        BlobContainerClient client = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(contentContainerName);
        client.createIfNotExists();
        return client;
    }

    private String escapeOData(String value) {
        return value.replace("'", "''");
    }

    private TableClient getOrCreateTable(String tableName) {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
        serviceClient.createTableIfNotExists(tableName);
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(tableName)
                .buildClient();
    }

    private void getOrCreateQueue(String queueName) {
        QueueClient queueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(queueName)
                .buildClient();
        queueClient.createIfNotExists();
    }

    private void getOrCreateContainer(String containerName) {
        BlobContainerClient client = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(containerName);
        client.createIfNotExists();
    }
}
