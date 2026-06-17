package com.webcrawler.fetcher.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class ContentHasher {

    public String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    public String computeHash(String content) {
        return computeHash(content.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256(byte[] content) {
        return computeHash(content);
    }
}
