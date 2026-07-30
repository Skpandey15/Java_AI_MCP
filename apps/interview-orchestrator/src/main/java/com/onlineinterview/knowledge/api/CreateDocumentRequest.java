package com.onlineinterview.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "text/plain|text/markdown") String mediaType,
        @NotBlank @Size(max = 500_000) String content) {}
