package com.onlineinterview.session.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @Min(1) @Max(100) int order,
        @NotBlank @Size(max = 4000) String prompt,
        @Min(1) @Max(100) int maxScore) {
}
