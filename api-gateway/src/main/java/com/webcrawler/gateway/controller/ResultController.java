package com.webcrawler.gateway.controller;

import com.webcrawler.gateway.dto.CrawlResultResponse;
import com.webcrawler.gateway.service.ResultService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/results")
public class ResultController {

    private final ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @GetMapping("/{jobId}")
    public Map<String, Object> listResults(@PathVariable String jobId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        List<CrawlResultResponse> results = resultService.listResults(jobId, page, size);
        return Map.of(
                "jobId", jobId,
                "page", page,
                "size", size,
                "total", resultService.countResults(jobId),
                "items", results
        );
    }

    @GetMapping(value = "/{jobId}/{urlHash}/content", produces = MediaType.TEXT_HTML_VALUE)
    public String getRawHtml(@PathVariable String jobId, @PathVariable String urlHash) {
        return resultService.getRawHtml(jobId, urlHash);
    }
}
