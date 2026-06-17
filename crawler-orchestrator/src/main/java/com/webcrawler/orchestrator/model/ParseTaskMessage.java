package com.webcrawler.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseTaskMessage {

    private String jobId;
    private String url;
    private String urlHash;
    private String blobPath;
    private Integer level;
}
