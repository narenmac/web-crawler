package com.webcrawler.gateway.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResultResponse {

    private String jobId;
    private String urlHash;
    private String url;
    private String title;
    private String contentType;
    private Long contentLength;
    private String blobPath;
    private Instant crawledAt;
}
