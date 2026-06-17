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

    public List<String> extractLinks(String sourceUrl, String html) {
        Document document = Jsoup.parse(html, sourceUrl);
        Set<String> links = new LinkedHashSet<>();

        for (Element element : document.select("a[href]")) {
            String normalized = normalizeUrl(sourceUrl, element.attr("href"));
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
            URI resolved = base.resolve(href.trim());
            URI normalized = new URI(
                    resolved.getScheme(),
                    resolved.getAuthority(),
                    resolved.getPath(),
                    resolved.getQuery(),
                    null);

            if (normalized.getScheme() == null) {
                return null;
            }

            String scheme = normalized.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }

            return normalized.normalize().toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return null;
        }
    }
}
