package com.webcrawler.orchestrator.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.webcrawler.orchestrator.model.JobRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JobManager {

    private final String storageConnectionString;
    private final Map<String, JobRecord> jobs = new ConcurrentHashMap<>();

    public JobManager(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
    }

    public synchronized JobRecord createJob(String userId, Collection<String> seedUrls, Integer maxDepth, Integer maxUrls) {
        if (hasRunningJob()) {
            throw new IllegalStateException("A crawl job is already running");
        }

        Instant now = Instant.now();
        JobRecord job = JobRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .status("CREATED")
                .seedUrls(seedUrls.stream().toList())
                .currentLevel(0)
                .maxDepth(maxDepth)
                .maxUrls(maxUrls)
                .enqueuedUrlCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        jobs.put(job.getId(), job);

        // TODO: Query Azure Table Storage for RUNNING jobs and persist the new entity if none exist.
        getJobTableClient();
        return job;
    }

    public synchronized JobRecord startJob(String jobId) {
        JobRecord job = requireJob(jobId);
        job.setStatus("RUNNING");
        job.setUpdatedAt(Instant.now());

        // TODO: Update the Azure Table entity status to RUNNING.
        getJobTableClient();
        return job;
    }

    public synchronized JobRecord stopJob(String jobId) {
        JobRecord job = requireJob(jobId);
        job.setStatus("STOPPED");
        job.setUpdatedAt(Instant.now());

        // TODO: Persist the STOPPED state to Azure Table Storage.
        getJobTableClient();
        return job;
    }

    public synchronized JobRecord completeJob(String jobId) {
        JobRecord job = requireJob(jobId);
        job.setStatus("COMPLETED");
        job.setUpdatedAt(Instant.now());

        // TODO: Persist completion details and metrics to Azure Table Storage.
        getJobTableClient();
        return job;
    }

    public synchronized void incrementEnqueuedCount(String jobId, int increment) {
        JobRecord job = requireJob(jobId);
        job.setEnqueuedUrlCount(job.getEnqueuedUrlCount() + increment);
        job.setUpdatedAt(Instant.now());
    }

    public synchronized boolean hasRunningJob() {
        // TODO: Replace this in-memory check with a query for RUNNING jobs in Azure Table Storage.
        getJobTableClient();
        return jobs.values().stream().anyMatch(job -> "RUNNING".equals(job.getStatus()));
    }

    public JobRecord getJob(String jobId) {
        return requireJob(jobId);
    }

    private JobRecord requireJob(String jobId) {
        JobRecord job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Unknown job id: " + jobId);
        }
        return job;
    }

    private TableClient getJobTableClient() {
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("jobs")
                .buildClient();
    }
}
