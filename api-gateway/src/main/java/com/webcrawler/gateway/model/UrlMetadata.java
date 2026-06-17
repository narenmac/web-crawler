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
public class UrlMetadata {

    private String partitionKey;
    private String rowKey;
    private String url;
    private String status;
    private Integer bfsLevel;
    private String contentHash;
    private String blobPath;
    private String parentUrl;
    private Instant crawledAt;

    public TableEntity toEntity() {
        return new TableEntity(partitionKey, rowKey)
                .addProperty("url", url)
                .addProperty("status", status)
                .addProperty("bfsLevel", bfsLevel)
                .addProperty("contentHash", contentHash)
                .addProperty("blobPath", blobPath)
                .addProperty("parentUrl", parentUrl)
                .addProperty("crawledAt", crawledAt != null ? crawledAt.toString() : null);
    }

    public static UrlMetadata fromEntity(TableEntity entity) {
        return UrlMetadata.builder()
                .partitionKey(entity.getPartitionKey())
                .rowKey(entity.getRowKey())
                .url(asString(entity.getProperty("url")))
                .status(asString(entity.getProperty("status")))
                .bfsLevel(asInteger(entity.getProperty("bfsLevel")))
                .contentHash(asString(entity.getProperty("contentHash")))
                .blobPath(asString(entity.getProperty("blobPath")))
                .parentUrl(asString(entity.getProperty("parentUrl")))
                .crawledAt(asInstant(entity.getProperty("crawledAt")))
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
