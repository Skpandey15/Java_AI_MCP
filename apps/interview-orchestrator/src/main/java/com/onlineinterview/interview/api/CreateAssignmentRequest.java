package com.onlineinterview.interview.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentRequest(
        @NotNull UUID candidateId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(1) @Max(10) int maxAttempts) {
}
