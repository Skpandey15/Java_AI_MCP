package com.onlineinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import com.onlineinterview.session.domain.QuestionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiQuestionServiceTest {
    @Test
    void persistsStructuredQuestionsWithGenerationMetadata() {
        var definitions = mock(InterviewDefinitionRepository.class);
        var questions = mock(ManualQuestionRepository.class);
        var client = mock(AiQuestionClient.class);
        var service = new AiQuestionService(definitions, questions, client);
        var interview = InterviewDefinition.draft("owner", "Java AI", "Senior interview",
                List.of("Java", "Spring AI"), InterviewDifficulty.HARD,
                QuestionMode.DIRECT_LLM, 60, 1);
        UUID requestId = UUID.randomUUID();
        when(questions.findByGenerationRequestIdOrderByOrderAsc(requestId)).thenReturn(List.of());
        when(definitions.findById(interview.getId())).thenReturn(Optional.of(interview));
        when(client.generate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AiQuestionClient.GenerationResponse(requestId, interview.getId(),
                        "interview-question-model", "direct-question-v1",
                        List.of(new AiQuestionClient.GeneratedQuestion(
                                1, "Explain a production RAG architecture.", 10,
                                QuestionType.LONG_TEXT, List.of(), List.of()))));
        when(questions.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generate("owner", interview.getId(), requestId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getGenerationRequestId()).isEqualTo(requestId);
        assertThat(result.getFirst().getModelPolicy()).isEqualTo("interview-question-model");
    }
}
