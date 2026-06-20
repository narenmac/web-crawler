package com.webcrawler.orchestrator.controller;

import com.webcrawler.orchestrator.model.JobRecord;
import com.webcrawler.orchestrator.service.JobManager;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/jobs")
public class JobInternalController {

    private final JobManager jobManager;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startJob(@RequestBody Map<String, Object> payload) {
        String jobId = (String) payload.get("jobId");
        String userId = (String) payload.get("userId");
        String seedFileUrl = (String) payload.get("seedFileUrl");
        Integer maxUrls = payload.get("maxUrls") != null ? ((Number) payload.get("maxUrls")).intValue() : null;

        log.info("Received start request for job {} from user {}, seedFile={}", jobId, userId, seedFileUrl);

        try {
            JobRecord job = jobManager.startJob(userId, jobId, seedFileUrl, maxUrls);
            return ResponseEntity.ok(Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus()
            ));
        } catch (IllegalStateException e) {
            log.warn("Cannot start job: {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
