package com.webcrawler.fetcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FetchService {

    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final Duration timeout;
    private final String userAgent;
    private final int maxRedirects;

    public FetchService(@Value("${app.fetch.timeout-ms:10000}") long timeoutMs,
                        @Value("${app.fetch.user-agent:WebCrawler/1.0}") String userAgent,
                        @Value("${app.fetch.max-redirects:5}") int maxRedirects) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.userAgent = userAgent;
        this.maxRedirects = maxRedirects;
    }

    public FetchResult fetch(String url) {
        URI initialUri;
        try {
            initialUri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new FetchFailedException("Invalid URL: " + url, ex, null, false);
        }

        FetchFailedException lastTransientFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return fetchOnce(initialUri, url);
            } catch (FetchFailedException ex) {
                if (!ex.isRetryable() || attempt == MAX_RETRIES) {
                    throw ex;
                }
                lastTransientFailure = ex;
                log.warn("Transient fetch failure for {} (attempt {}/{}). Retrying.", url, attempt, MAX_RETRIES, ex);
                sleep(attempt);
            }
        }

        throw lastTransientFailure == null
                ? new FetchFailedException("Unable to fetch " + url, null, null, true)
                : lastTransientFailure;
    }

    private FetchResult fetchOnce(URI initialUri, String originalUrl) {
        try {
            URI currentUri = initialUri;
            for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
                HttpRequest request = HttpRequest.newBuilder(currentUri)
                        .GET()
                        .timeout(timeout)
                        .header("User-Agent", userAgent)
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (!isRedirect(response.statusCode())) {
                    if (response.statusCode() >= 500) {
                        throw new FetchFailedException(
                                "Transient HTTP error " + response.statusCode() + " while fetching " + originalUrl,
                                null,
                                response.statusCode(),
                                true);
                    }
                    if (response.statusCode() >= 400) {
                        throw new FetchFailedException(
                                "Permanent HTTP error " + response.statusCode() + " while fetching " + originalUrl,
                                null,
                                response.statusCode(),
                                false);
                    }
                    return new FetchResult(response.body(), response.statusCode(), contentType(response.headers()));
                }

                if (redirectCount == maxRedirects) {
                    throw new FetchFailedException("Too many redirects while fetching " + originalUrl, null, null, false);
                }

                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new FetchFailedException(
                                "Redirect response missing Location header for " + originalUrl,
                                null,
                                response.statusCode(),
                                false));
                currentUri = currentUri.resolve(location);
            }

            throw new FetchFailedException("Unable to fetch " + originalUrl, null, null, true);
        } catch (HttpTimeoutException | ConnectException ex) {
            throw new FetchFailedException("Transient network error while fetching " + originalUrl, ex, null, true);
        } catch (IOException ex) {
            throw new FetchFailedException("I/O error while fetching " + originalUrl, ex, null, true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FetchFailedException("Fetch interrupted for " + originalUrl, ex, null, true);
        } catch (IllegalArgumentException ex) {
            throw new FetchFailedException("Invalid URL: " + originalUrl, ex, null, false);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private String contentType(HttpHeaders headers) {
        return headers.firstValue("Content-Type").orElse("application/octet-stream");
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FetchFailedException("Interrupted while retrying fetch", ex, null, true);
        }
    }

    public static final class FetchFailedException extends RuntimeException {

        private final Integer statusCode;
        private final boolean retryable;

        public FetchFailedException(String message, Throwable cause, Integer statusCode, boolean retryable) {
            super(message, cause);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }

        public Integer getStatusCode() {
            return statusCode;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
