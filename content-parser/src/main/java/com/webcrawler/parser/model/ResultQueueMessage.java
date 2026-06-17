package com.webcrawler.parser.model;

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
    private String sourceUrl;

    @Builder.Default
    private List<String> discoveredUrls = new ArrayList<>();

    private Integer level;
}
