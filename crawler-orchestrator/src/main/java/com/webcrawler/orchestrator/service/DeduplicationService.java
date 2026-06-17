package com.webcrawler.orchestrator.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DeduplicationService {

    private final Map<String, Set<String>> visitedByJob = new ConcurrentHashMap<>();

    public boolean markVisited(String jobId, String url) {
        String normalized = normalize(url);
        return visitedByJob
                .computeIfAbsent(jobId, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalized);
    }

    public Set<String> visitedUrls(String jobId) {
        return Collections.unmodifiableSet(visitedByJob.getOrDefault(jobId, Set.of()));
    }

    public void clear(String jobId) {
        visitedByJob.remove(jobId);
    }

    private String normalize(String url) {
        return url == null ? "" : url.trim().toLowerCase();
    }
}
