package com.webcrawler.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultQueueMessage {

    private String jobId;
    private String parentUrl;

    @Builder.Default
    @JsonAlias("discoveredUrls")
    private List<String> discoveredLinks = new ArrayList<>();

    @JsonAlias("level")
    private Integer parentBfsLevel;
}
