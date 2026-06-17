package com.webcrawler.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<@NotBlank String> seedUrls;

    @Min(1)
    @Max(25)
    private Integer maxDepth;

    @Min(1)
    @Max(10000)
    private Integer maxUrls;
}
