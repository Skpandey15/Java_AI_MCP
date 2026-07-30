package com.onlineinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.domain.QuestionComposition;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import com.onlineinterview.session.domain.QuestionType;
import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiQuestionServiceTest {
    @Test
    void persistsStructuredQuestionsWithGenerationMetadata() {
        var definitions = mock(InterviewDefinitionRepository.class);
        var questions = mock(ManualQuestionRepository.class);
        var client = mock(AiQuestionClient.class);
        var knowledge = mock(KnowledgeService.class);
        var service = new AiQuestionService(definitions, questions, client, knowledge);
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
                                QuestionType.LONG_TEXT, List.of(), List.of(), List.of()))));
        when(questions.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generate("owner", interview.getId(), requestId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getGenerationRequestId()).isEqualTo(requestId);
        assertThat(result.getFirst().getModelPolicy()).isEqualTo("interview-question-model");
    }

    @Test
    void retrievesAndPersistsOnlyAuthorizedRagCitations() {
        var definitions = mock(InterviewDefinitionRepository.class);
        var questions = mock(ManualQuestionRepository.class);
        var client = mock(AiQuestionClient.class);
        var knowledge = mock(KnowledgeService.class);
        var service = new AiQuestionService(definitions, questions, client, knowledge);
        var collectionId = UUID.randomUUID();
        var interview = InterviewDefinition.draft("owner", "Java RAG", "Grounded interview",
                List.of("Java"), InterviewDifficulty.MEDIUM, QuestionMode.RAG, 60, 1, 70,
                QuestionComposition.allLongText(1), collectionId);
        var requestId = UUID.randomUUID();
        var hit = new KnowledgeVectorStore.SearchHit(UUID.randomUUID(), UUID.randomUUID(),
                "java-guide.md", 2, "Records are immutable data carriers.", 0.91);
        when(questions.findByGenerationRequestIdOrderByOrderAsc(requestId)).thenReturn(List.of());
        when(definitions.findById(interview.getId())).thenReturn(Optional.of(interview));
        when(knowledge.search("owner", collectionId, "Java RAG Java", 8))
                .thenReturn(List.of(hit));
        when(client.generate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AiQuestionClient.GenerationResponse(requestId, interview.getId(),
                        "interview-question-model", "rag-question-v1",
                        List.of(new AiQuestionClient.GeneratedQuestion(
                                1, "Explain the purpose of a Java record.", 10,
                                QuestionType.LONG_TEXT, List.of(), List.of(),
                                List.of(hit.chunkId())))));
        when(questions.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generate("owner", interview.getId(), requestId);

        assertThat(result.getFirst().getSource().name()).isEqualTo("AI_RAG");
        assertThat(result.getFirst().getCitations()).singleElement().satisfies(citation -> {
            assertThat(citation.getChunkId()).isEqualTo(hit.chunkId());
            assertThat(citation.getFileName()).isEqualTo("java-guide.md");
        });
    }

    @Test
    void rejectsCitationNotReturnedByAuthorizedRetrieval() {
        var definitions = mock(InterviewDefinitionRepository.class);
        var questions = mock(ManualQuestionRepository.class);
        var client = mock(AiQuestionClient.class);
        var knowledge = mock(KnowledgeService.class);
        var service = new AiQuestionService(definitions, questions, client, knowledge);
        var collectionId = UUID.randomUUID();
        var interview = InterviewDefinition.draft("owner", "Java RAG", "Grounded interview",
                List.of("Java"), InterviewDifficulty.MEDIUM, QuestionMode.RAG, 60, 1, 70,
                QuestionComposition.allLongText(1), collectionId);
        var requestId = UUID.randomUUID();
        var hit = new KnowledgeVectorStore.SearchHit(UUID.randomUUID(), UUID.randomUUID(),
                "java-guide.md", 0, "Java reference.", 0.8);
        when(questions.findByGenerationRequestIdOrderByOrderAsc(requestId)).thenReturn(List.of());
        when(definitions.findById(interview.getId())).thenReturn(Optional.of(interview));
        when(knowledge.search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(collectionId),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(8)))
                .thenReturn(List.of(hit));
        when(client.generate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AiQuestionClient.GenerationResponse(requestId, interview.getId(), "model", "v1",
                        List.of(new AiQuestionClient.GeneratedQuestion(1,
                                "Explain this Java reference.", 10, QuestionType.LONG_TEXT,
                                List.of(), List.of(), List.of(UUID.randomUUID())))));

        assertThatThrownBy(() -> service.generate("owner", interview.getId(), requestId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unauthorized citation");
    }
}
