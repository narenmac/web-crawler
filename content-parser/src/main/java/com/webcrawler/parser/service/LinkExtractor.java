package com.webcrawler.parser.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LinkExtractor {

    public List<String> extractLinks(String html, String baseUrl) {
        if (!StringUtils.hasText(html) || !StringUtils.hasText(baseUrl)) {
            return List.of();
        }
        Document document = Jsoup.parse(html, baseUrl);
        Set<String> links = new LinkedHashSet<>();

        for (Element element : document.select("a[href]")) {
            String href = element.attr("abs:href");
            if (!StringUtils.hasText(href)) {
                href = element.attr("href");
            }
            String normalized = normalizeUrl(baseUrl, href);
            if (normalized != null) {
                links.add(normalized);
            }
        }

        return new ArrayList<>(links);
    }

    private String normalizeUrl(String baseUrl, String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }

        try {
            URI base = URI.create(baseUrl);
            URI resolved = base.resolve(href.trim()).normalize();
            if (resolved.getScheme() == null) {
                return null;
            }

            String scheme = resolved.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }

            URI normalized = new URI(
                    scheme,
                    resolved.getUserInfo(),
                    resolved.getHost() == null ? null : resolved.getHost().toLowerCase(),
                    resolved.getPort(),
                    resolved.getPath(),
                    resolved.getQuery(),
                    null);
            return normalized.normalize().toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return null;
        }
    }
}
