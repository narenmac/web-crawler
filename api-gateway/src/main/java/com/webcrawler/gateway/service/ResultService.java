package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.webcrawler.gateway.dto.CrawlResultResponse;
import com.webcrawler.gateway.model.UrlMetadata;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultService {

    @Value("${app.azure.storage.connection-string}")
    private String storageConnectionString;

    @Value("${app.azure.storage.content-container:crawler-content}")
    private String contentContainerName;

    public List<CrawlResultResponse> getResults(String jobId, int page, int size, String statusFilter, String searchQuery) {
        return filteredResults(jobId, statusFilter, searchQuery).stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toResponse)
                .toList();
    }

    public long countResults(String jobId, String statusFilter, String searchQuery) {
        return filteredResults(jobId, statusFilter, searchQuery).size();
    }

    public String getContent(String jobId, String urlHash) {
        String blobPath = "raw-html/%s/%s.html".formatted(jobId, urlHash);
        BlobContainerClient containerClient = rawHtmlContainerClient();
        try {
            if (!containerClient.getBlobClient(blobPath).exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Raw HTML content not found");
            }
            return containerClient.getBlobClient(blobPath).downloadContent().toString();
        } catch (BlobStorageException ex) {
            if (ex.getStatusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Raw HTML content not found");
            }
            throw ex;
        }
    }

    private List<UrlMetadata> filteredResults(String jobId, String statusFilter, String searchQuery) {
        String normalizedStatus = statusFilter == null ? null : statusFilter.trim().toUpperCase(Locale.ROOT);
        String normalizedQuery = searchQuery == null ? null : searchQuery.trim().toLowerCase(Locale.ROOT);

        return StreamSupport.stream(getResultTableClient()
                        .listEntities(new ListEntitiesOptions()
                                .setFilter("PartitionKey eq '%s'".formatted(escapeOData(jobId))), null, null)
                        .spliterator(), false)
                .map(UrlMetadata::fromEntity)
                .filter(metadata -> !StringUtils.hasText(normalizedStatus)
                        || normalizedStatus.equalsIgnoreCase(metadata.getStatus()))
                .filter(metadata -> !StringUtils.hasText(normalizedQuery)
                        || (metadata.getUrl() != null && metadata.getUrl().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                        || (metadata.getParentUrl() != null && metadata.getParentUrl().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
                .toList();
    }

    private CrawlResultResponse toResponse(UrlMetadata metadata) {
        return CrawlResultResponse.builder()
                .urlHash(metadata.getRowKey())
                .url(metadata.getUrl())
                .status(metadata.getStatus())
                .bfsLevel(metadata.getBfsLevel())
                .contentHash(metadata.getContentHash())
                .blobPath(metadata.getBlobPath())
                .parentUrl(metadata.getParentUrl())
                .crawledAt(metadata.getCrawledAt())
                .build();
    }

    private TableClient getResultTableClient() {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient();
        serviceClient.createTableIfNotExists("urlmetadata");
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("urlmetadata")
                .buildClient();
    }

    private BlobContainerClient rawHtmlContainerClient() {
        BlobContainerClient client = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient(contentContainerName);
        client.createIfNotExists();
        return client;
    }

    private String escapeOData(String value) {
        return value.replace("'", "''");
    }
}
