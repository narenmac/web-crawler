package com.webcrawler.gateway.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMetadata {

    private String partitionKey;
    private String rowKey;
    private String jobId;
    private String urlHash;
    private String url;
    private String title;
    private String contentType;
    private Long contentLength;
    private String blobPath;
    private Instant crawledAt;
}
