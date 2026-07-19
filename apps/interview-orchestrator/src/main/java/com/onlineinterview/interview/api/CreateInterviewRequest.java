package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateInterviewRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> skills,
        @NotNull InterviewDifficulty difficulty,
        @NotNull QuestionMode questionMode,
        @Min(5) @Max(480) int durationMinutes,
        @Min(1) @Max(100) int questionCount,
        @Min(1) @Max(100) int passingPercentage) {
}
