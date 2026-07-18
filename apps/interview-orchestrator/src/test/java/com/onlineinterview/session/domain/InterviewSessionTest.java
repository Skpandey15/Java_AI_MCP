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

class InterviewSessionTest {
    @Test
    void serverExpiresSessionAtCalculatedDeadline() {
        Instant now = Instant.parse("2026-07-18T10:00:00Z");
        var definition = InterviewDefinition.draft("owner", "Java", "Java interview",
                List.of("Java"), InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1);
        definition.publish();
        var assignment = InterviewAssignment.schedule(definition, UUID.randomUUID(),
                now.minusSeconds(60), now.plusSeconds(7200), 1);
        var session = InterviewSession.start(assignment, assignment.getCandidateId(), now);

        session.enforceExpiry(now.plusSeconds(3600));

        assertThat(session.getState()).isEqualTo(SessionState.EXPIRED);
        assertThatThrownBy(() -> session.submit(now.plusSeconds(3601)))
                .isInstanceOf(IllegalStateException.class);
    }
}
