package com.onlineinterview.review.api;

import jakarta.validation.constraints.Size;

public record FinalizeReviewRequest(@Size(max = 4000) String feedback) {
}
