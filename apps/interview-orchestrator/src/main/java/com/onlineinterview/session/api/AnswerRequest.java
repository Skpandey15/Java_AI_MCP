package com.onlineinterview.session.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnswerRequest(
        @NotNull @Size(max = 12000) String content,
        long expectedVersion) {
}
