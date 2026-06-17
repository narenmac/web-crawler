package com.webcrawler.fetcher.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PolitenessService {

    private final Duration defaultDelay;
    private final ConcurrentMap<String, Instant> lastFetchByDomain = new ConcurrentHashMap<>();

    public PolitenessService(@Value("${app.fetch.politeness-delay:PT1S}") Duration defaultDelay) {
        this.defaultDelay = defaultDelay;
    }

    public Duration remainingDelay(URI uri, RobotsTxtService.RobotsTxtRules rules) {
        String domainKey = domainKey(uri);
        if (domainKey == null) {
            return Duration.ZERO;
        }

        Duration requiredDelay = defaultDelay;
        if (rules != null && rules.crawlDelay() != null && rules.crawlDelay().compareTo(requiredDelay) > 0) {
            requiredDelay = rules.crawlDelay();
        }

        Instant lastFetch = lastFetchByDomain.get(domainKey);
        if (lastFetch == null) {
            return Duration.ZERO;
        }

        Duration elapsed = Duration.between(lastFetch, Instant.now());
        if (!elapsed.isNegative() && elapsed.compareTo(requiredDelay) >= 0) {
            return Duration.ZERO;
        }

        return requiredDelay.minus(elapsed.isNegative() ? Duration.ZERO : elapsed);
    }

    public void recordFetch(URI uri) {
        String domainKey = domainKey(uri);
        if (domainKey != null) {
            lastFetchByDomain.put(domainKey, Instant.now());
        }
    }

    private String domainKey(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        StringBuilder builder = new StringBuilder()
                .append(uri.getScheme().toLowerCase(Locale.ROOT))
                .append("://")
                .append(uri.getHost().toLowerCase(Locale.ROOT));
        if (uri.getPort() > 0) {
            builder.append(':').append(uri.getPort());
        }
        return builder.toString();
    }
}
