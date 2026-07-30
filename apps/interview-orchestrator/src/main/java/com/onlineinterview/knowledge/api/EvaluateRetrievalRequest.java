package com.onlineinterview.knowledge.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record EvaluateRetrievalRequest(
        @NotEmpty @Size(max = 100) List<@Valid EvaluationCase> cases) {
    public record EvaluationCase(
            @NotBlank @Size(max = 1000) String query,
            @NotEmpty @Size(max = 20) Set<UUID> expectedChunkIds) {}
}
