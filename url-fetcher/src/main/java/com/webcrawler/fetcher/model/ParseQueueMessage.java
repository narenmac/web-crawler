package com.webcrawler.fetcher.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("bfsLevel")
    @JsonAlias("level")
    private Integer bfsLevel;

    private String parentUrl;
}
