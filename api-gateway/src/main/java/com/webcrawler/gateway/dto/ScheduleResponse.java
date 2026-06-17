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
public class ScheduleResponse {

    private String id;
    private String seedFileName;
    private String intervalType;
    private String cronExpression;
    private Instant nextRunAt;
    private String lastRunStatus;
    private boolean enabled;
}
