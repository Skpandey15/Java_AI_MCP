package com.onlineinterview.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.application.KnowledgeService;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSearchToolHandler implements McpToolHandler {
    private final InterviewDefinitionRepository interviews;
    private final KnowledgeService knowledge;
    private final ObjectMapper mapper;

    public KnowledgeSearchToolHandler(InterviewDefinitionRepository interviews,
            KnowledgeService knowledge, ObjectMapper mapper) {
        this.interviews = interviews;
        this.knowledge = knowledge;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "knowledge"; }
    @Override public String toolName() { return "search_knowledge"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        var collectionId = InterviewContextToolHandler.parseId(arguments, "collectionId");
        String query = arguments.path("query").asText("").trim();
        if (!"INTERVIEW".equals(context.resourceType()) || query.isBlank()) {
            throw new McpProtocolException(-32602, "Authorized interview and query are required");
        }
        interviews.findById(context.resourceId())
                .filter(value -> value.getOwnerSubject().equals(context.actorSubject()))
                .filter(value -> collectionId.equals(value.getKnowledgeCollectionId()))
                .orElseThrow(() -> new McpProtocolException(
                        -32003, "Knowledge collection is not bound to the interview"));
        var hits = knowledge.search(context.actorSubject(), collectionId, query, 8);
        var citations = mapper.createArrayNode();
        hits.forEach(hit -> citations.addObject()
                .put("chunkId", hit.chunkId().toString())
                .put("documentId", hit.documentId().toString())
                .put("fileName", hit.fileName())
                .put("chunkIndex", hit.chunkIndex())
                .put("content", hit.content())
                .put("score", hit.score()));
        return mapper.createObjectNode().set("citations", citations);
    }
}
