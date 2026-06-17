package com.webcrawler.fetcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FetchService {

    private final HttpClient httpClient;
    private final Duration timeout;
    private final String userAgent;
    private final long politenessDelayMs;

    public FetchService(@Value("${app.fetch.timeout-ms:10000}") long timeoutMs,
                        @Value("${app.fetch.user-agent:WebCrawlerBot/1.0}") String userAgent,
                        @Value("${app.fetch.politeness-delay-ms:1000}") long politenessDelayMs) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.userAgent = userAgent;
        this.politenessDelayMs = politenessDelayMs;
    }

    public byte[] fetch(String url) throws IOException, InterruptedException {
        Thread.sleep(politenessDelayMs);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }
}
