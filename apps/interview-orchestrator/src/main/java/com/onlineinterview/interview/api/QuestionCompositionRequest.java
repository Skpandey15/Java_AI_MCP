package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.QuestionComposition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuestionCompositionRequest(
        @Min(0) @Max(100) int mcqSingle,
        @Min(0) @Max(100) int mcqMultiple,
        @Min(0) @Max(100) int shortText,
        @Min(0) @Max(100) int longText) {

    QuestionComposition toDomain() {
        return new QuestionComposition(mcqSingle, mcqMultiple, shortText, longText);
    }
}
