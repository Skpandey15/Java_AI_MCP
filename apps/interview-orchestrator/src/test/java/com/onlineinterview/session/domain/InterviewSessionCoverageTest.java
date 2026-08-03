package com.onlineinterview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewSessionCoverageTest {
    private final Instant now = Instant.parse("2026-07-18T10:00:00Z");

    private InterviewSession started() {
        var definition = InterviewDefinition.draft("owner", "Java", "desc", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1, 70);
        definition.publish();
        var assignment = InterviewAssignment.schedule(definition, UUID.randomUUID(),
                now.minusSeconds(60), now.plusSeconds(7200), 1);
        return InterviewSession.start(assignment, assignment.getCandidateId(), now);
    }

    private InterviewSession submitted() {
        var session = started();
        session.submit(now.plusSeconds(60));
        return session;
    }

    @Test
    void startExposesGettersAndDoesNotExpireEarly() {
        var session = started();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getState()).isEqualTo(SessionState.IN_PROGRESS);
        assertThat(session.getStartedAt()).isEqualTo(now);
        assertThat(session.getExpiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(session.getReviewStatus()).isEqualTo(ReviewStatus.NOT_SUBMITTED);
        assertThat(session.getCandidateId()).isNotNull();
        assertThat(session.getAssignment()).isNotNull();
        session.enforceExpiry(now.plusSeconds(60));
        assertThat(session.getState()).isEqualTo(SessionState.IN_PROGRESS);
    }

    @Test
    void recordsObjectiveScoreOnlyAfterSubmit() {
        assertThatThrownBy(() -> started().recordObjectiveScore(3))
                .isInstanceOf(IllegalStateException.class);
        var session = submitted();
        session.recordObjectiveScore(4);
        assertThat(session.getObjectiveScore()).isEqualTo(4);
        assertThat(session.getSubmittedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void cannotSubmitTwice() {
        var session = submitted();
        assertThatThrownBy(() -> session.submit(now.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finalizeReviewValidatesInputs() {
        assertThatThrownBy(() -> submitted().finalizeReview(1, 0, 70, "f", "r", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> submitted().finalizeReview(11, 10, 70, "f", "r", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> submitted().finalizeReview(5, 10, 0, "f", "r", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finalizeReviewRecordsOutcome() {
        var session = submitted();
        session.finalizeReview(8, 10, 70, "well done", "reviewer", now.plusSeconds(120));
        assertThat(session.getTotalScore()).isEqualTo(8);
        assertThat(session.getResultOutcome()).isEqualTo(ResultOutcome.PASSED);
        assertThat(session.getReviewFeedback()).isEqualTo("well done");
        assertThat(session.getReviewStatus()).isEqualTo(ReviewStatus.REVIEWED);
    }
}
