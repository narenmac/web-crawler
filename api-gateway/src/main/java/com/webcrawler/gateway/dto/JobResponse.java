package com.webcrawler.gateway.dto;

import java.time.Instant;
import java.util.List;
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
    private String userId;
    private String name;
    private List<String> seedUrls;
    private String seedBlobPath;
    private String status;
    private Integer maxDepth;
    private Integer maxUrls;
    private Instant createdAt;
    private Instant updatedAt;
}
