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
public class JobResponse {

    private String id;
    private String status;
    private Integer totalUrls;
    private Integer crawledUrls;
    private Integer currentBfsLevel;
    private Integer maxUrls;
    private Instant createdAt;
    private Instant completedAt;
    private String source;
}
