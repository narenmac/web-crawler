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

    private String urlHash;
    private String url;
    private String status;
    private Integer bfsLevel;
    private String contentHash;
    private String blobPath;
    private String parentUrl;
    private Instant crawledAt;
}
