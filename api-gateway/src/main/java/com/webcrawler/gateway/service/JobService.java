package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.webcrawler.gateway.dto.CreateJobRequest;
import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.model.Job;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private final String storageConnectionString;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public JobService(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    public JobResponse createJob(String userId, CreateJobRequest request, MultipartFile seedFile) {
        Instant now = Instant.now();
        String jobId = UUID.randomUUID().toString();
        String seedBlobPath = seedFile != null && !seedFile.isEmpty()
                ? "seed-files/%s/%s/%s".formatted(userId, jobId, seedFile.getOriginalFilename())
                : null;

        Job job = Job.builder()
                .id(jobId)
                .partitionKey(userId)
                .rowKey(jobId)
                .userId(userId)
                .name(request.getName())
                .seedUrls(new ArrayList<>(request.getSeedUrls()))
                .seedBlobPath(seedBlobPath)
                .status("CREATED")
                .maxDepth(request.getMaxDepth())
                .maxUrls(request.getMaxUrls())
                .createdAt(now)
                .updatedAt(now)
                .build();

        jobs.put(jobId, job);

        if (seedFile != null && !seedFile.isEmpty()) {
            // TODO: Upload the seed file contents with blobClient.upload(seedFile.getInputStream(), seedFile.getSize()).
            BlobClient blobClient = seedFileBlobClient(seedBlobPath);
        }

        // TODO: Persist the job entity with getJobTableClient().upsertEntity(...).
        getJobTableClient();

        return toResponse(job);
    }

    public List<JobResponse> listJobs(String userId) {
        // TODO: Replace the in-memory filter with an Azure Table query on the user's partition key.
        getJobTableClient();
        return jobs.values().stream()
                .filter(job -> userId.equals(job.getUserId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public JobResponse getJob(String id, String userId) {
        return toResponse(findOwnedJob(id, userId));
    }

    public JobResponse stopJob(String id, String userId) {
        Job job = findOwnedJob(id, userId);
        job.setStatus("STOP_REQUESTED");
        job.setUpdatedAt(Instant.now());

        // TODO: Update the Azure Table entity and publish a stop signal to the orchestrator control queue.
        getJobTableClient();
        return toResponse(job);
    }

    public void deleteJob(String id, String userId) {
        Job job = findOwnedJob(id, userId);
        jobs.remove(job.getId());

        // TODO: Delete the Azure Table entity and any seed file blob associated with the job.
        getJobTableClient();
    }

    private Job findOwnedJob(String id, String userId) {
        Job job = jobs.get(id);
        if (job == null || !userId.equals(job.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return job;
    }

    private JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .userId(job.getUserId())
                .name(job.getName())
                .seedUrls(job.getSeedUrls())
                .seedBlobPath(job.getSeedBlobPath())
                .status(job.getStatus())
                .maxDepth(job.getMaxDepth())
                .maxUrls(job.getMaxUrls())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private TableClient getJobTableClient() {
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("jobs")
                .buildClient();
    }

    private BlobClient seedFileBlobClient(String blobPath) {
        BlobContainerClient containerClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient("crawler-input");
        return containerClient.getBlobClient(blobPath);
    }
}
