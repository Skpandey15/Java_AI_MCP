package com.onlineinterview.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchKnowledgeRequest(
        @NotBlank @Size(max = 2000) String query,
        @Min(1) @Max(20) int limit) {}
