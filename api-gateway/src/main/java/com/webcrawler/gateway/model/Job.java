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
public class Job {

    private String partitionKey;
    private String rowKey;
    private String status;
    private Integer totalUrls;
    private Integer crawledUrls;
    private Integer currentBfsLevel;
    private Integer maxUrls;
    private Instant createdAt;
    private Instant completedAt;
    private String source;

    public TableEntity toEntity() {
        return new TableEntity(partitionKey, rowKey)
                .addProperty("status", status)
                .addProperty("totalUrls", totalUrls)
                .addProperty("crawledUrls", crawledUrls)
                .addProperty("currentBfsLevel", currentBfsLevel)
                .addProperty("maxUrls", maxUrls)
                .addProperty("createdAt", createdAt != null ? createdAt.toString() : null)
                .addProperty("completedAt", completedAt != null ? completedAt.toString() : null)
                .addProperty("source", source);
    }

    public static Job fromEntity(TableEntity entity) {
        return Job.builder()
                .partitionKey(entity.getPartitionKey())
                .rowKey(entity.getRowKey())
                .status(asString(entity.getProperty("status")))
                .totalUrls(asInteger(entity.getProperty("totalUrls")))
                .crawledUrls(asInteger(entity.getProperty("crawledUrls")))
                .currentBfsLevel(asInteger(entity.getProperty("currentBfsLevel")))
                .maxUrls(asInteger(entity.getProperty("maxUrls")))
                .createdAt(asInstant(entity.getProperty("createdAt")))
                .completedAt(asInstant(entity.getProperty("completedAt")))
                .source(asString(entity.getProperty("source")))
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Instant asInstant(Object value) {
        return value == null ? null : Instant.parse(String.valueOf(value));
    }
}
