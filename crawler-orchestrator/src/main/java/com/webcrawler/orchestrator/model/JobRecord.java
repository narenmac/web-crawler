package com.webcrawler.orchestrator.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRecord {

    private String id;
    private String userId;
    private String status;

    @Builder.Default
    private List<String> seedUrls = new ArrayList<>();

    private Integer currentLevel;
    private Integer maxDepth;
    private Integer maxUrls;
    private Integer enqueuedUrlCount;
    private Instant createdAt;
    private Instant updatedAt;
}
