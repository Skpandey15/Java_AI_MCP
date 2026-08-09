package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateInterviewRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> skills,
        @NotNull InterviewDifficulty difficulty,
        @NotNull QuestionMode questionMode,
        @Min(5) @Max(480) int durationMinutes,
        @Min(0) @Max(100) int questionCount,
        @NotNull @Valid QuestionCompositionRequest questionComposition,
        @Min(1) @Max(100) int passingPercentage,
        UUID knowledgeCollectionId) {

    /** Adaptive interviews generate every question at runtime, so they carry no fixed question
     *  set (questionCount 0). Every other mode still requires at least one question. */
    @AssertTrue(message = "questionCount must be at least 1 unless the interview mode is ADAPTIVE")
    public boolean isQuestionCountValidForMode() {
        return questionMode == QuestionMode.ADAPTIVE || questionCount >= 1;
    }
}
