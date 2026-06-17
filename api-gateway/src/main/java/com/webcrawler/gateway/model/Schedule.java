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
public class Schedule {

    private String id;
    private String partitionKey;
    private String rowKey;
    private String userId;
    private String name;
    private String cronExpression;

    @Builder.Default
    private List<String> seedUrls = new ArrayList<>();

    private Integer maxDepth;
    private Integer maxUrls;
    private Boolean enabled;
    private String lastTriggeredJobId;
    private Instant nextExecutionAt;
    private Instant createdAt;
    private Instant updatedAt;
}
