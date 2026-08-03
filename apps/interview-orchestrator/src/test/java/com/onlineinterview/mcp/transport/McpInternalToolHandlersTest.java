package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpAiEvaluationRepository;
import com.onlineinterview.session.domain.*;
import com.onlineinterview.session.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpInternalToolHandlersTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final InterviewDefinitionRepository interviews =
            mock(InterviewDefinitionRepository.class);

    @Test
    void returnsOnlyBoundOwnedInterviewContext() {
        var definition = mock(InterviewDefinition.class);
        var id = UUID.randomUUID();
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(definition.getTitle()).thenReturn("Java");
        when(definition.getSkills()).thenReturn(List.of("Spring"));
        when(interviews.findById(id)).thenReturn(Optional.of(definition));
        var handler = new InterviewContextToolHandler(interviews, mapper);
        var args = mapper.createObjectNode().put("interviewId", id.toString());

        var result = handler.execute(context("interview", "get_interview_context",
                "INTERVIEW", id, "owner"), args);
        assertThat(result.path("title").asText()).isEqualTo("Java");
        assertThat(result.path("skills").get(0).asText()).isEqualTo("Spring");
        assertThatThrownBy(() -> handler.execute(context("interview",
                "get_interview_context", "INTERVIEW", UUID.randomUUID(), "owner"), args))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> InterviewContextToolHandler.parseId(
                mapper.createObjectNode().put("id", "bad"), "id"))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void searchesQuestionsOnlyForAuthorizedInterviewSkillAndLimit() {
        var questions = mock(ManualQuestionRepository.class);
        var definition = mock(InterviewDefinition.class);
        var question = mock(ManualQuestion.class);
        var id = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        when(definition.getId()).thenReturn(id);
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(definition.getSkills()).thenReturn(List.of("Java"));
        when(question.getId()).thenReturn(questionId);
        when(interviews.findById(id)).thenReturn(Optional.of(definition));
        when(questions.findByInterviewDefinitionIdOrderByOrderAsc(id))
                .thenReturn(List.of(question));
        var handler = new QuestionSearchToolHandler(interviews, questions, mapper);

        var result = handler.execute(context("question-bank",
                "search_approved_questions", "INTERVIEW", id, "owner"),
                mapper.createObjectNode().put("skill", "java").put("limit", 1));
        assertThat(result.path("questionIds").get(0).asText())
                .isEqualTo(questionId.toString());
        assertThatThrownBy(() -> handler.execute(context("question-bank",
                "search_approved_questions", "INTERVIEW", id, "owner"),
                mapper.createObjectNode().put("skill", "").put("limit", 21)))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void searchesOnlyKnowledgeCollectionBoundToInterview() {
        var knowledge = mock(KnowledgeService.class);
        var definition = mock(InterviewDefinition.class);
        var interviewId = UUID.randomUUID();
        var collectionId = UUID.randomUUID();
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(definition.getKnowledgeCollectionId()).thenReturn(collectionId);
        when(interviews.findById(interviewId)).thenReturn(Optional.of(definition));
        when(knowledge.search("owner", collectionId, "spring", 8)).thenReturn(List.of(
                new KnowledgeVectorStore.SearchHit(UUID.randomUUID(), UUID.randomUUID(),
                        "guide.md", 2, "Spring context", .9)));
        var handler = new KnowledgeSearchToolHandler(interviews, knowledge, mapper);

        var result = handler.execute(context("knowledge", "search_knowledge",
                "INTERVIEW", interviewId, "owner"), mapper.createObjectNode()
                .put("collectionId", collectionId.toString()).put("query", "spring"));
        assertThat(result.path("citations").get(0).path("fileName").asText())
                .isEqualTo("guide.md");
        assertThat(result.path("citations").get(0).path("content").asText())
                .isEqualTo("Spring context");
        assertThatThrownBy(() -> handler.execute(context("knowledge", "search_knowledge",
                "INTERVIEW", interviewId, "owner"), mapper.createObjectNode()
                .put("collectionId", UUID.randomUUID().toString()).put("query", "spring")))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void persistsAiEvaluationAsPendingHumanReview() {
        var sessions = mock(InterviewSessionRepository.class);
        var evaluations = mock(McpAiEvaluationRepository.class);
        var questions = mock(ManualQuestionRepository.class);
        var session = mock(InterviewSession.class);
        var assignment = mock(InterviewAssignment.class);
        var definition = mock(InterviewDefinition.class);
        var question = mock(ManualQuestion.class);
        var sessionId = UUID.randomUUID();
        var interviewId = UUID.randomUUID();
        when(session.getState()).thenReturn(SessionState.SUBMITTED);
        when(session.getReviewStatus()).thenReturn(ReviewStatus.PENDING_REVIEW);
        when(session.getAssignment()).thenReturn(assignment);
        when(assignment.getInterviewDefinition()).thenReturn(definition);
        when(definition.getId()).thenReturn(interviewId);
        when(question.getMaxScore()).thenReturn(10);
        when(questions.findByInterviewDefinitionIdOrderByOrderAsc(interviewId))
                .thenReturn(List.of(question));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        var handler = new SubmitAiEvaluationToolHandler(
                sessions, evaluations, questions, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        var context = context(
                "result", "submit_ai_evaluation", "SESSION", sessionId, "service");

        var result = handler.execute(context, mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("score", 8));
        assertThat(result.path("accepted").asBoolean()).isTrue();
        var saved = ArgumentCaptor.forClass(McpAiEvaluation.class);
        verify(evaluations).save(saved.capture());
        assertThat(saved.getValue().getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getValue().getProposedScore()).isEqualTo(8);
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING_REVIEW");

        assertThatThrownBy(() -> handler.execute(context, mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("score", -1)))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> handler.execute(context, mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("score", 11)))
                .isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("maximum");
    }

    private static McpAuthorizationContext context(String server, String tool,
            String type, UUID resourceId, String actor) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.QUESTION_GENERATION,
                server, tool, actor, McpActorRole.INTERVIEWER, type, resourceId,
                5, false, NOW, NOW.plusSeconds(60));
    }
}
