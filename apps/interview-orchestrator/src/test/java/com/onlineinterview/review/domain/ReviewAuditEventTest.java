package com.onlineinterview.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewAuditEventTest {
    private final Instant now = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void buildsAnswerScoredAndReviewFinalizedEvents() {
        var session = UUID.randomUUID();
        var answer = UUID.randomUUID();

        var scored = ReviewAuditEvent.answerScored(session, answer, "reviewer", 8, "solid", now);
        var finalized = ReviewAuditEvent.reviewFinalized(session, "reviewer", 42, "done", now);

        assertThat(scored).isNotNull();
        assertThat(finalized).isNotNull();
        assertThat(ReviewAuditEventType.valueOf("ANSWER_SCORED"))
                .isEqualTo(ReviewAuditEventType.ANSWER_SCORED);
        assertThat(ReviewAuditEventType.values()).contains(ReviewAuditEventType.REVIEW_FINALIZED);
    }
}
