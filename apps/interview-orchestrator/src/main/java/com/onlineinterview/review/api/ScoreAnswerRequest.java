package com.onlineinterview.review.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ScoreAnswerRequest(
        @Min(0) @Max(100) int score,
        @Size(max = 4000) String feedback) {
}
