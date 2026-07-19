package com.onlineinterview.session.api;

import com.onlineinterview.session.domain.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuestionRequest(
        @Min(1) @Max(100) int order,
        @NotBlank @Size(max = 4000) String prompt,
        @Min(1) @Max(100) int maxScore,
        @NotNull QuestionType type,
        @Size(max = 20) List<@NotBlank @Size(max = 1000) String> options,
        @Size(max = 20) List<@NotBlank @Size(max = 1000) String> correctAnswers) {
    public QuestionRequest {
        options = options == null ? List.of() : List.copyOf(options);
        correctAnswers = correctAnswers == null ? List.of() : List.copyOf(correctAnswers);
    }
}
