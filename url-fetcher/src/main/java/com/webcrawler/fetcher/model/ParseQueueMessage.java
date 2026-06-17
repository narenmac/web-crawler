package com.webcrawler.fetcher.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseQueueMessage {

    private String jobId;
    private String url;
    private String urlHash;
    private String blobPath;
    private Integer level;
}
