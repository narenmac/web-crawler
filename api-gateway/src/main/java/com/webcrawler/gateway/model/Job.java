package com.webcrawler.gateway.model;

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
public class Job {

    private String id;
    private String partitionKey;
    private String rowKey;
    private String userId;
    private String name;

    @Builder.Default
    private List<String> seedUrls = new ArrayList<>();

    private String seedBlobPath;
    private String status;
    private Integer maxDepth;
    private Integer maxUrls;
    private Instant createdAt;
    private Instant updatedAt;
}
