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
public class UrlQueueMessage {

    private String jobId;
    private String url;

    @JsonProperty("bfsLevel")
    @JsonAlias("level")
    private Integer bfsLevel;

    private String parentUrl;
}
