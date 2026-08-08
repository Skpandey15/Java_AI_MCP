package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.knowledge.domain.KnowledgeChunk;
import com.onlineinterview.knowledge.domain.KnowledgeCollection;
import com.onlineinterview.knowledge.domain.KnowledgeDocument;
import com.onlineinterview.knowledge.infrastructure.KnowledgeChunkRepository;
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

    @Test
    void returnsSkillBlueprintForBoundOwnedInterview() {
        var definition = mock(InterviewDefinition.class);
        var id = UUID.randomUUID();
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(definition.getSkills()).thenReturn(List.of("Concurrency"));
        when(definition.getDifficulty()).thenReturn(InterviewDifficulty.HARD);
        when(definition.getPassingPercentage()).thenReturn(70);
        when(definition.getQuestionCount()).thenReturn(6);
        when(interviews.findById(id)).thenReturn(Optional.of(definition));
        var handler = new SkillBlueprintToolHandler(interviews, mapper);
        var args = mapper.createObjectNode().put("interviewId", id.toString());

        var result = handler.execute(context("interview", "get_skill_blueprint",
                "INTERVIEW", id, "owner"), args);
        assertThat(result.path("targetDifficulty").asText()).isEqualTo("HARD");
        assertThat(result.path("passingPercentage").asInt()).isEqualTo(70);
        assertThat(result.path("questionCount").asInt()).isEqualTo(6);
        assertThat(result.path("skills").get(0).path("name").asText()).isEqualTo("Concurrency");
        assertThat(result.path("skills").get(0).path("targetDifficulty").asText()).isEqualTo("HARD");
        // resource-binding mismatch (different bound id)
        assertThatThrownBy(() -> handler.execute(context("interview", "get_skill_blueprint",
                "INTERVIEW", UUID.randomUUID(), "owner"), args))
                .isInstanceOf(McpProtocolException.class);
        // right interview, wrong owner
        assertThatThrownBy(() -> handler.execute(context("interview", "get_skill_blueprint",
                "INTERVIEW", id, "intruder"), args))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void reportsQuestionReuseWithinAuthorizedSession() {
        var answers = mock(InterviewAnswerRepository.class);
        var answer = mock(InterviewAnswer.class);
        var sessionId = UUID.randomUUID();
        var askedQuestion = UUID.randomUUID();
        var freshQuestion = UUID.randomUUID();
        var askedAt = Instant.parse("2026-07-30T09:00:00Z");
        when(answer.getUpdatedAt()).thenReturn(askedAt);
        when(answers.findBySession_IdAndQuestion_Id(sessionId, askedQuestion))
                .thenReturn(Optional.of(answer));
        when(answers.findBySession_IdAndQuestion_Id(sessionId, freshQuestion))
                .thenReturn(Optional.empty());
        var handler = new QuestionReuseToolHandler(answers, mapper);

        var reused = handler.execute(context("question-bank", "check_question_reuse",
                "SESSION", sessionId, "service"), mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("questionId", askedQuestion.toString()));
        assertThat(reused.path("reused").asBoolean()).isTrue();
        assertThat(reused.path("previouslyAskedAt").asText()).isEqualTo(askedAt.toString());

        var fresh = handler.execute(context("question-bank", "check_question_reuse",
                "SESSION", sessionId, "service"), mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("questionId", freshQuestion.toString()));
        assertThat(fresh.path("reused").asBoolean()).isFalse();

        assertThatThrownBy(() -> handler.execute(context("question-bank", "check_question_reuse",
                "SESSION", UUID.randomUUID(), "service"), mapper.createObjectNode()
                .put("sessionId", sessionId.toString()).put("questionId", askedQuestion.toString())))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void returnsCitationOnlyFromCollectionBoundToInterview() {
        var chunks = mock(KnowledgeChunkRepository.class);
        var definition = mock(InterviewDefinition.class);
        var chunk = mock(KnowledgeChunk.class);
        var document = mock(KnowledgeDocument.class);
        var collection = mock(KnowledgeCollection.class);
        var interviewId = UUID.randomUUID();
        var collectionId = UUID.randomUUID();
        var chunkId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(definition.getKnowledgeCollectionId()).thenReturn(collectionId);
        when(interviews.findById(interviewId)).thenReturn(Optional.of(definition));
        when(collection.getId()).thenReturn(collectionId);
        when(document.getCollection()).thenReturn(collection);
        when(document.getId()).thenReturn(documentId);
        when(document.getFileName()).thenReturn("guide.md");
        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getDocument()).thenReturn(document);
        when(chunk.getIndex()).thenReturn(3);
        when(chunk.getContent()).thenReturn("Spring context");
        when(chunks.findById(chunkId)).thenReturn(Optional.of(chunk));
        var handler = new CitationToolHandler(interviews, chunks, mapper);
        var args = mapper.createObjectNode()
                .put("collectionId", collectionId.toString()).put("chunkId", chunkId.toString());

        var result = handler.execute(context("knowledge", "get_citation",
                "INTERVIEW", interviewId, "owner"), args);
        assertThat(result.path("fileName").asText()).isEqualTo("guide.md");
        assertThat(result.path("documentId").asText()).isEqualTo(documentId.toString());
        assertThat(result.path("chunkIndex").asInt()).isEqualTo(3);
        assertThat(result.path("content").asText()).isEqualTo("Spring context");
        // collection not bound to the interview
        assertThatThrownBy(() -> handler.execute(context("knowledge", "get_citation",
                "INTERVIEW", interviewId, "owner"), mapper.createObjectNode()
                .put("collectionId", UUID.randomUUID().toString()).put("chunkId", chunkId.toString())))
                .isInstanceOf(McpProtocolException.class);
        // chunk id not found
        var missing = UUID.randomUUID();
        when(chunks.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.execute(context("knowledge", "get_citation",
                "INTERVIEW", interviewId, "owner"), mapper.createObjectNode()
                .put("collectionId", collectionId.toString()).put("chunkId", missing.toString())))
                .isInstanceOf(McpProtocolException.class);
        // wrong resource type
        assertThatThrownBy(() -> handler.execute(context("knowledge", "get_citation",
                "SESSION", interviewId, "owner"), args))
                .isInstanceOf(McpProtocolException.class);
    }

    private static McpAuthorizationContext context(String server, String tool,
            String type, UUID resourceId, String actor) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.QUESTION_GENERATION,
                server, tool, actor, McpActorRole.INTERVIEWER, type, resourceId,
                5, false, NOW, NOW.plusSeconds(60));
    }
}
