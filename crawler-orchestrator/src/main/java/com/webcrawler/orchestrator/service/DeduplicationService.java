package com.webcrawler.orchestrator.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableServiceException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class DeduplicationService {

    private final TableClient tableClient;

    public DeduplicationService(@Value("${app.azure.storage.connection-string}") String storageConnectionString,
                                @Value("${app.azure.tables.urlmetadata:urlmetadata}") String urlMetadataTableName) {
        new com.azure.data.tables.TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .createTableIfNotExists(urlMetadataTableName);
        this.tableClient = new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName(urlMetadataTableName)
                .buildClient();
    }

    public boolean isUrlVisited(String jobId, String url) {
        String normalizedUrl = normalize(url);
        if (!StringUtils.hasText(normalizedUrl)) {
            return true;
        }

        try {
            tableClient.getEntity(jobId, hash(normalizedUrl));
            return true;
        } catch (TableServiceException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    public boolean markUrlVisited(String jobId, String url, int bfsLevel, String parentUrl) {
        String normalizedUrl = normalize(url);
        if (!StringUtils.hasText(normalizedUrl)) {
            return false;
        }

        Instant now = Instant.now();
        TableEntity entity = new TableEntity(jobId, hash(normalizedUrl))
                .addProperty("url", normalizedUrl)
                .addProperty("bfsLevel", bfsLevel)
                .addProperty("status", "QUEUED")
                .addProperty("createdAt", now.toString())
                .addProperty("updatedAt", now.toString());
        if (StringUtils.hasText(parentUrl)) {
            entity.addProperty("parentUrl", parentUrl);
        }

        try {
            tableClient.createEntity(entity);
            return true;
        } catch (TableServiceException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusCode() == 409) {
                return false;
            }
            throw ex;
        }
    }

    public long getTotalUrlCount(String jobId) {
        PagedIterable<TableEntity> entities = tableClient.listEntities(
                new ListEntitiesOptions().setFilter("PartitionKey eq '%s'".formatted(escapeFilterValue(jobId))),
                null,
                null);
        return entities.stream().count();
    }

    private String normalize(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        try {
            URI uri = new URI(url.trim()).normalize();
            if (uri.getScheme() == null || uri.getHost() == null) {
                return url.trim();
            }

            URI normalized = new URI(
                    uri.getScheme().toLowerCase(),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null);
            return normalized.toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            log.debug("Falling back to trimmed URL for deduplication: {}", url, ex);
            return url.trim();
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "''");
    }
}
