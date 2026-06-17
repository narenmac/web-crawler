package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.CreateJobRequest;
import com.webcrawler.gateway.dto.JobResponse;
import com.webcrawler.gateway.security.CurrentUserProvider;
import com.webcrawler.gateway.service.JobService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jobs")
public class JobController {

    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final int MAX_URL_LINES = 10_000;

    private final JobService jobService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> createJob(@Valid @ModelAttribute CreateJobRequest request) {
        MultipartFile seedFile = request.getSeedFile();
        validateSeedFile(seedFile);
        JobResponse response = jobService.createJob(currentUserProvider.getCurrentUserId(), seedFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<JobResponse> listJobs() {
        return jobService.getJobs(currentUserProvider.getCurrentUserId());
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable String id) {
        return jobService.getJob(currentUserProvider.getCurrentUserId(), id);
    }

    @PostMapping("/{id}/stop")
    public JobResponse stopJob(@PathVariable String id) {
        return jobService.stopJob(currentUserProvider.getCurrentUserId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        jobService.deleteJob(currentUserProvider.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private void validateSeedFile(MultipartFile seedFile) {
        if (seedFile == null || seedFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file is required");
        }

        String fileName = seedFile.getOriginalFilename() == null ? "" : seedFile.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".txt") && !fileName.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file must be a .txt or .csv file");
        }

        if (seedFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file must be 1MB or smaller");
        }

        try {
            long lineCount = seedFile.getInputStream()
                    .transferTo(new java.io.ByteArrayOutputStream());
            String content = new String(seedFile.getBytes(), StandardCharsets.UTF_8);
            long nonEmptyLines = content.lines().filter(line -> !line.isBlank()).count();
            if (nonEmptyLines == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file must contain at least one URL");
            }
            if (nonEmptyLines > MAX_URL_LINES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seed file cannot exceed 10000 lines");
            }
            log.debug("Validated seed file {} with {} bytes and {} URLs", seedFile.getOriginalFilename(), lineCount, nonEmptyLines);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read seed file", ex);
        }
    }
}
