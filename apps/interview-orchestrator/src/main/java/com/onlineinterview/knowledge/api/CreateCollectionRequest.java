package com.onlineinterview.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 1000) String description) {}
