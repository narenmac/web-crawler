package com.webcrawler.orchestrator.service;

import com.webcrawler.orchestrator.listener.JobControlListener;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class BfsScheduler {

    private final QueuePublisher queuePublisher;
    private final DeduplicationService deduplicationService;
    private final JobManager jobManager;
    private final JobControlListener jobControlListener;
    private final int maxUrls;
    private final Map<String, Integer> activeLevelByJob = new ConcurrentHashMap<>();

    public BfsScheduler(QueuePublisher queuePublisher,
                        DeduplicationService deduplicationService,
                        JobManager jobManager,
                        JobControlListener jobControlListener,
                        @Value("${app.max-urls:10000}") int maxUrls) {
        this.queuePublisher = queuePublisher;
        this.deduplicationService = deduplicationService;
        this.jobManager = jobManager;
        this.jobControlListener = jobControlListener;
        this.maxUrls = maxUrls;
    }

    public void enqueueSeedUrls(String jobId, Collection<String> seedUrls) {
        activeLevelByJob.put(jobId, 0);
        enqueueUrls(jobId, seedUrls, 0, null);
    }

    public void markLevelCompleted(String jobId, int level) {
        activeLevelByJob.put(jobId, level + 1);
        log.info("BFS level {} completed for job {}", level, jobId);
    }

    public void enqueueDiscoveredUrls(String jobId, int currentLevel, Collection<String> discoveredUrls) {
        processDiscoveredLinks(jobId, discoveredUrls, currentLevel, null);
    }

    public void processDiscoveredLinks(String jobId, Collection<String> links, int parentBfsLevel) {
        processDiscoveredLinks(jobId, links, parentBfsLevel, null);
    }

    public void processDiscoveredLinks(String jobId, Collection<String> links, int parentBfsLevel, String parentUrl) {
        if (jobControlListener.isStopRequested(jobId)) {
            log.info("Skipping discovered links for job {} because a stop has been requested", jobId);
            return;
        }

        enqueueUrls(jobId, links, parentBfsLevel + 1, parentUrl);
    }

    public int currentLevel(String jobId) {
        return activeLevelByJob.getOrDefault(jobId, 0);
    }

    private void enqueueUrls(String jobId, Collection<String> urls, int bfsLevel, String parentUrl) {
        if (urls == null || urls.isEmpty()) {
            return;
        }

        long currentCount = deduplicationService.getTotalUrlCount(jobId);
        int enqueued = 0;

        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            if (jobControlListener.isStopRequested(jobId)) {
                log.info("Stopping BFS scheduling for job {} at level {}", jobId, bfsLevel);
                break;
            }
            if (currentCount >= maxUrls) {
                log.warn("Max URL cap of {} reached for job {}", maxUrls, jobId);
                break;
            }
            if (deduplicationService.isUrlVisited(jobId, url)) {
                continue;
            }
            if (!deduplicationService.markUrlVisited(jobId, url, bfsLevel, parentUrl)) {
                continue;
            }

            queuePublisher.publishToUrlQueue(jobId, url, bfsLevel, parentUrl);
            currentCount++;
            enqueued++;
        }

        if (enqueued > 0) {
            jobManager.incrementEnqueuedCount(jobId, enqueued);
            activeLevelByJob.put(jobId, bfsLevel);
            log.info("Enqueued {} URLs for job {} at BFS level {}", enqueued, jobId, bfsLevel);
        }
    }
}
