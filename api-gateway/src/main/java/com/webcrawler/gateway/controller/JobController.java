package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.CreateJobRequest;
import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestPart("request") CreateJobRequest request,
                                                 @RequestPart(value = "seedFile", required = false) MultipartFile seedFile,
                                                 Authentication authentication) {
        JobResponse response = jobService.createJob(currentUser(authentication), request, seedFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<JobResponse> listJobs(Authentication authentication) {
        return jobService.listJobs(currentUser(authentication));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable String id, Authentication authentication) {
        return jobService.getJob(id, currentUser(authentication));
    }

    @PostMapping("/{id}/stop")
    public JobResponse stopJob(@PathVariable String id, Authentication authentication) {
        return jobService.stopJob(id, currentUser(authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id, Authentication authentication) {
        jobService.deleteJob(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private String currentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
