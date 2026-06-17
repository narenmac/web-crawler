package com.webcrawler.gateway.model;

import com.azure.data.tables.models.TableEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {

    private String partitionKey;
    private String rowKey;
    private String intervalType;
    private String cronExpression;
    private String seedFileUrl;
    private boolean enabled;
    private String lastRunJobId;
    private Instant nextRunAt;
    private String lastRunStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public TableEntity toEntity() {
        return new TableEntity(partitionKey, rowKey)
                .addProperty("intervalType", intervalType)
                .addProperty("cronExpression", cronExpression)
                .addProperty("seedFileUrl", seedFileUrl)
                .addProperty("enabled", enabled)
                .addProperty("lastRunJobId", lastRunJobId)
                .addProperty("nextRunAt", nextRunAt != null ? nextRunAt.toString() : null)
                .addProperty("lastRunStatus", lastRunStatus)
                .addProperty("createdAt", createdAt != null ? createdAt.toString() : null)
                .addProperty("updatedAt", updatedAt != null ? updatedAt.toString() : null);
    }

    public static Schedule fromEntity(TableEntity entity) {
        return Schedule.builder()
                .partitionKey(entity.getPartitionKey())
                .rowKey(entity.getRowKey())
                .intervalType(asString(entity.getProperty("intervalType")))
                .cronExpression(asString(entity.getProperty("cronExpression")))
                .seedFileUrl(asString(entity.getProperty("seedFileUrl")))
                .enabled(asBoolean(entity.getProperty("enabled")))
                .lastRunJobId(asString(entity.getProperty("lastRunJobId")))
                .nextRunAt(asInstant(entity.getProperty("nextRunAt")))
                .lastRunStatus(asString(entity.getProperty("lastRunStatus")))
                .createdAt(asInstant(entity.getProperty("createdAt")))
                .updatedAt(asInstant(entity.getProperty("updatedAt")))
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Instant asInstant(Object value) {
        return value == null ? null : Instant.parse(String.valueOf(value));
    }
}
