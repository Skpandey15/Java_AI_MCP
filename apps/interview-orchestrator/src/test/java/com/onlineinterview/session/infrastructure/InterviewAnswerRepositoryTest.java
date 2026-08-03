package com.onlineinterview.session.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.onlineinterview.session.domain.InterviewAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.data.core.PropertyPath;

class InterviewAnswerRepositoryTest {
    @Test
    void repositoryQueriesTraverseMappedAssociations() {
        assertThatCode(() -> PropertyPath.from("session.id", InterviewAnswer.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> PropertyPath.from("question.id", InterviewAnswer.class))
                .doesNotThrowAnyException();
    }
}
