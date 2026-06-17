package com.webcrawler.orchestrator.service;

import com.webcrawler.orchestrator.model.UrlTaskMessage;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BfsScheduler {

    private final QueuePublisher queuePublisher;
    private final DeduplicationService deduplicationService;
    private final JobManager jobManager;
    private final int maxUrls;
    private final Map<String, Integer> activeLevelByJob = new ConcurrentHashMap<>();

    public BfsScheduler(QueuePublisher queuePublisher,
                        DeduplicationService deduplicationService,
                        JobManager jobManager,
                        @Value("${app.max-urls:10000}") int maxUrls) {
        this.queuePublisher = queuePublisher;
        this.deduplicationService = deduplicationService;
        this.jobManager = jobManager;
        this.maxUrls = maxUrls;
    }

    public void enqueueSeedUrls(String jobId, Collection<String> seedUrls) {
        activeLevelByJob.put(jobId, 0);
        enqueueUrls(jobId, 0, seedUrls);
    }

    public void markLevelCompleted(String jobId, int level) {
        activeLevelByJob.put(jobId, level + 1);
        log.info("BFS level {} completed for job {}", level, jobId);
    }

    public void enqueueDiscoveredUrls(String jobId, int currentLevel, Collection<String> discoveredUrls) {
        enqueueUrls(jobId, currentLevel + 1, discoveredUrls);
    }

    public int currentLevel(String jobId) {
        return activeLevelByJob.getOrDefault(jobId, 0);
    }

    private void enqueueUrls(String jobId, int level, Collection<String> urls) {
        Set<String> visited = deduplicationService.visitedUrls(jobId);
        int queuedCount = visited.size();

        for (String url : urls) {
            if (queuedCount >= maxUrls) {
                log.warn("Max URL cap of {} reached for job {}", maxUrls, jobId);
                break;
            }

            if (!deduplicationService.markVisited(jobId, url)) {
                continue;
            }

            queuePublisher.publishUrlTask(UrlTaskMessage.builder()
                    .jobId(jobId)
                    .url(url)
                    .level(level)
                    .build());
            queuedCount++;
        }

        jobManager.incrementEnqueuedCount(jobId, Math.max(0, queuedCount - visited.size()));
        activeLevelByJob.put(jobId, level);
    }
}
