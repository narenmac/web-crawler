package com.webcrawler.fetcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class RobotsTxtService {

    private static final RobotsTxtRules ALLOW_ALL = new RobotsTxtRules(Collections.emptyList(), null);

    private final HttpClient httpClient;
    private final Duration timeout;
    private final String userAgent;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<String, CachedRobotsTxt> cache = new ConcurrentHashMap<>();

    public RobotsTxtService(@Value("${app.fetch.timeout-ms:10000}") long timeoutMs,
                            @Value("${app.fetch.user-agent:WebCrawler/1.0}") String userAgent,
                            @Value("${app.fetch.robots.cache-ttl:PT1H}") Duration cacheTtl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.userAgent = userAgent;
        this.cacheTtl = cacheTtl;
    }

    public RobotsTxtRules getRules(URI targetUri) {
        String domainKey = domainKey(targetUri);
        if (domainKey == null) {
            return ALLOW_ALL;
        }

        CachedRobotsTxt cached = cache.get(domainKey);
        if (cached != null && !cached.isExpired()) {
            return cached.rules();
        }

        RobotsTxtRules rules = fetchAndParse(targetUri);
        cache.put(domainKey, new CachedRobotsTxt(rules, Instant.now().plus(cacheTtl)));
        return rules;
    }

    public boolean isAllowed(URI targetUri, RobotsTxtRules rules) {
        return rules.isAllowed(normalizePath(targetUri));
    }

    private RobotsTxtRules fetchAndParse(URI targetUri) {
        try {
            URI robotsUri = new URI(targetUri.getScheme(), null, targetUri.getHost(), targetUri.getPort(), "/robots.txt", null, null);
            HttpRequest request = HttpRequest.newBuilder(robotsUri)
                    .GET()
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                return ALLOW_ALL;
            }
            if (response.statusCode() >= 500) {
                log.warn("Robots.txt request failed for {} with status {}", targetUri, response.statusCode());
                return ALLOW_ALL;
            }
            return parse(response.body());
        } catch (IOException ex) {
            log.warn("Unable to fetch robots.txt for {}", targetUri, ex);
            return ALLOW_ALL;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while fetching robots.txt for {}", targetUri, ex);
            return ALLOW_ALL;
        } catch (Exception ex) {
            log.warn("Unable to parse robots.txt for {}", targetUri, ex);
            return ALLOW_ALL;
        }
    }

    private RobotsTxtRules parse(String content) {
        if (!StringUtils.hasText(content)) {
            return ALLOW_ALL;
        }

        List<RobotsGroup> groups = new ArrayList<>();
        RobotsGroup currentGroup = null;
        boolean rulesStarted = false;

        for (String rawLine : content.split("\\R")) {
            String line = sanitizeLine(rawLine);
            if (!StringUtils.hasText(line)) {
                currentGroup = null;
                rulesStarted = false;
                continue;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }

            String directive = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separatorIndex + 1).trim();

            if ("user-agent".equals(directive)) {
                if (currentGroup == null || rulesStarted) {
                    currentGroup = new RobotsGroup();
                    groups.add(currentGroup);
                    rulesStarted = false;
                }
                if (StringUtils.hasText(value)) {
                    currentGroup.userAgents().add(value.toLowerCase(Locale.ROOT));
                }
                continue;
            }

            if (currentGroup == null) {
                continue;
            }

            rulesStarted = true;
            if ("allow".equals(directive) || "disallow".equals(directive)) {
                String normalizedRule = normalizeRulePath(value);
                if (normalizedRule != null) {
                    currentGroup.rules().add(new Rule("allow".equals(directive), normalizedRule));
                }
            } else if ("crawl-delay".equals(directive) && currentGroup.crawlDelay() == null) {
                currentGroup.crawlDelay(parseCrawlDelay(value));
            }
        }

        return selectRules(groups);
    }

    private RobotsTxtRules selectRules(List<RobotsGroup> groups) {
        String normalizedUserAgent = userAgent.toLowerCase(Locale.ROOT);
        String userAgentToken = normalizedUserAgent.contains("/")
                ? normalizedUserAgent.substring(0, normalizedUserAgent.indexOf('/'))
                : normalizedUserAgent;

        RobotsGroup wildcardGroup = null;
        for (RobotsGroup group : groups) {
            for (String candidate : group.userAgents()) {
                if (normalizedUserAgent.startsWith(candidate) || userAgentToken.equals(candidate)) {
                    return new RobotsTxtRules(List.copyOf(group.rules()), group.crawlDelay());
                }
                if ("*".equals(candidate) && wildcardGroup == null) {
                    wildcardGroup = group;
                }
            }
        }

        if (wildcardGroup != null) {
            return new RobotsTxtRules(List.copyOf(wildcardGroup.rules()), wildcardGroup.crawlDelay());
        }

        return ALLOW_ALL;
    }

    private Duration parseCrawlDelay(String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (seconds <= 0) {
                return null;
            }
            return Duration.ofMillis((long) Math.ceil(seconds * 1000));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sanitizeLine(String rawLine) {
        int commentIndex = rawLine.indexOf('#');
        return (commentIndex >= 0 ? rawLine.substring(0, commentIndex) : rawLine).trim();
    }

    private String normalizeRulePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("*") || trimmed.contains("$")) {
            return null;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizePath(URI uri) {
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (StringUtils.hasText(uri.getRawQuery())) {
            path = path + "?" + uri.getRawQuery();
        }
        return path;
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

    public record RobotsTxtRules(List<Rule> rules, Duration crawlDelay) {

        public boolean isAllowed(String path) {
            Rule winningRule = null;
            for (Rule rule : rules) {
                if (!path.startsWith(rule.path())) {
                    continue;
                }
                if (winningRule == null
                        || rule.path().length() > winningRule.path().length()
                        || (rule.path().length() == winningRule.path().length() && rule.allowed())) {
                    winningRule = rule;
                }
            }
            return winningRule == null || winningRule.allowed();
        }
    }

    public record Rule(boolean allowed, String path) {
    }

    private record CachedRobotsTxt(RobotsTxtRules rules, Instant expiresAt) {

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private static final class RobotsGroup {

        private final List<String> userAgents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
        private Duration crawlDelay;

        private List<String> userAgents() {
            return userAgents;
        }

        private List<Rule> rules() {
            return rules;
        }

        private Duration crawlDelay() {
            return crawlDelay;
        }

        private void crawlDelay(Duration crawlDelay) {
            this.crawlDelay = crawlDelay;
        }
    }
}
