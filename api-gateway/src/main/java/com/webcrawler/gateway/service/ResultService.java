package com.webcrawler.gateway.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.webcrawler.gateway.dto.CrawlResultResponse;
import com.webcrawler.gateway.model.UrlMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResultService {

    private final String storageConnectionString;
    private final Map<String, List<UrlMetadata>> resultsByJob = new ConcurrentHashMap<>();

    public ResultService(@Value("${app.azure.storage.connection-string}") String storageConnectionString) {
        this.storageConnectionString = storageConnectionString;
        seedSampleResult();
    }

    public List<CrawlResultResponse> listResults(String jobId, int page, int size) {
        // TODO: Query Azure Table Storage for the job's result metadata with continuation token support.
        getResultTableClient();

        List<UrlMetadata> items = resultsByJob.getOrDefault(jobId, List.of());
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());

        return items.subList(fromIndex, toIndex).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long countResults(String jobId) {
        return resultsByJob.getOrDefault(jobId, List.of()).size();
    }

    public String getRawHtml(String jobId, String urlHash) {
        BlobClient blobClient = rawHtmlBlobClient(jobId, urlHash);
        // TODO: Replace the placeholder return value with blobClient.downloadContent().toString().
        return "<html><body><p>TODO: download raw HTML from " + blobClient.getBlobUrl() + "</p></body></html>";
    }

    private CrawlResultResponse toResponse(UrlMetadata metadata) {
        return CrawlResultResponse.builder()
                .jobId(metadata.getJobId())
                .urlHash(metadata.getUrlHash())
                .url(metadata.getUrl())
                .title(metadata.getTitle())
                .contentType(metadata.getContentType())
                .contentLength(metadata.getContentLength())
                .blobPath(metadata.getBlobPath())
                .crawledAt(metadata.getCrawledAt())
                .build();
    }

    private TableClient getResultTableClient() {
        return new TableClientBuilder()
                .connectionString(storageConnectionString)
                .tableName("url-metadata")
                .buildClient();
    }

    private BlobClient rawHtmlBlobClient(String jobId, String urlHash) {
        BlobContainerClient containerClient = new BlobServiceClientBuilder()
                .connectionString(storageConnectionString)
                .buildClient()
                .getBlobContainerClient("crawler-content");
        return containerClient.getBlobClient("raw-html/%s/%s.html".formatted(jobId, urlHash));
    }

    private void seedSampleResult() {
        UrlMetadata metadata = UrlMetadata.builder()
                .partitionKey("sample-job")
                .rowKey("sample-hash")
                .jobId("sample-job")
                .urlHash("sample-hash")
                .url("https://example.org")
                .title("Example")
                .contentType("text/html")
                .contentLength(128L)
                .blobPath("raw-html/sample-job/sample-hash.html")
                .crawledAt(Instant.now())
                .build();

        List<UrlMetadata> sampleResults = new ArrayList<>();
        sampleResults.add(metadata);
        resultsByJob.put("sample-job", sampleResults);
    }
}
