package com.webcrawler.fetcher.service;

public record FetchResult(byte[] content, int statusCode, String contentType) {
}
