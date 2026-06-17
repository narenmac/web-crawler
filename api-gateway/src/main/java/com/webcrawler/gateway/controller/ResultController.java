package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.CrawlResultResponse;
import com.webcrawler.gateway.security.CurrentUserProvider;
import com.webcrawler.gateway.service.JobService;
import com.webcrawler.gateway.service.ResultService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/results")
public class ResultController {

    private final ResultService resultService;
    private final JobService jobService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{jobId}")
    public Map<String, Object> listResults(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        jobService.getJob(currentUserProvider.getCurrentUserId(), jobId);
        List<CrawlResultResponse> results = resultService.getResults(jobId, page, size, status, q);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);
        response.put("page", page);
        response.put("size", size);
        response.put("statusFilter", status);
        response.put("searchQuery", q);
        response.put("total", resultService.countResults(jobId, status, q));
        response.put("items", results);
        return response;
    }

    @GetMapping(value = "/{jobId}/{urlHash}/content", produces = MediaType.TEXT_HTML_VALUE)
    public String getRawHtml(@PathVariable String jobId, @PathVariable String urlHash) {
        jobService.getJob(currentUserProvider.getCurrentUserId(), jobId);
        return resultService.getContent(jobId, urlHash);
    }
}
