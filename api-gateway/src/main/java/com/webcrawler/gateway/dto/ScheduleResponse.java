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
public class ScheduleResponse {

    private String id;
    private String userId;
    private String name;
    private String cronExpression;
    private List<String> seedUrls;
    private Integer maxDepth;
    private Integer maxUrls;
    private Boolean enabled;
    private String lastTriggeredJobId;
    private Instant nextExecutionAt;
    private Instant createdAt;
    private Instant updatedAt;
}
