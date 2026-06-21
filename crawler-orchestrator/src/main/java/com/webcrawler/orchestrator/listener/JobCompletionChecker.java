package com.webcrawler.orchestrator.listener;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.webcrawler.orchestrator.service.JobManager;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Detects when a running job has completed by checking if all queues are empty.
 * Uses consecutive idle checks to avoid premature completion during queue transitions.
 */
@Slf4j
@Component
public class JobCompletionChecker {

    private static final int IDLE_THRESHOLD = 5; // consecutive idle polls before marking complete

    private final JobManager jobManager;
    private final QueueClient urlQueueClient;
    private final QueueClient parseQueueClient;
    private final QueueClient resultQueueClient;
    private final AtomicInteger consecutiveIdleCount = new AtomicInteger(0);

    public JobCompletionChecker(JobManager jobManager,
                                @Value("${app.azure.storage.connection-string}") String storageConnectionString,
                                @Value("${app.azure.queues.url-queue}") String urlQueueName,
                                @Value("${app.azure.queues.parse-queue}") String parseQueueName,
                                @Value("${app.azure.queues.result-queue}") String resultQueueName) {
        this.jobManager = jobManager;
        this.urlQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(urlQueueName)
                .buildClient();
        this.parseQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(parseQueueName)
                .buildClient();
        this.resultQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(resultQueueName)
                .buildClient();
    }

    @Scheduled(fixedDelayString = "${app.polling.completion-check-ms:3000}")
    public void checkJobCompletion() {
        if (!jobManager.isJobRunning()) {
            consecutiveIdleCount.set(0);
            return;
        }

        try {
            int urlCount = getApproximateCount(urlQueueClient);
            int parseCount = getApproximateCount(parseQueueClient);
            int resultCount = getApproximateCount(resultQueueClient);

            if (urlCount == 0 && parseCount == 0 && resultCount == 0) {
                int idle = consecutiveIdleCount.incrementAndGet();
                log.debug("All queues empty, idle count: {}/{}", idle, IDLE_THRESHOLD);
                if (idle >= IDLE_THRESHOLD) {
                    // Find and complete the running job
                    jobManager.findRunningJobId().ifPresent(jobId -> {
                        jobManager.completeJob(jobId);
                        log.info("Job {} marked COMPLETED — all queues drained after {} idle checks", jobId, idle);
                    });
                    consecutiveIdleCount.set(0);
                }
            } else {
                consecutiveIdleCount.set(0);
            }
        } catch (Exception e) {
            log.debug("Completion check failed (will retry): {}", e.getMessage());
        }
    }

    private int getApproximateCount(QueueClient client) {
        try {
            client.createIfNotExists();
            return client.getProperties().getApproximateMessagesCount();
        } catch (Exception e) {
            return -1; // treat error as "not idle"
        }
    }
}
