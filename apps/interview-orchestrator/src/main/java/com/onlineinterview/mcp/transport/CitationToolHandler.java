package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeChunkRepository;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import org.springframework.stereotype.Component;

/** Knowledge MCP tool: returns a single citation (the source chunk) by id, so an agent can
 *  attribute a grounded follow-up or its scoring. Read-only; the chunk must belong to the
 *  knowledge collection bound to the authorized interview. */
@Component
public class CitationToolHandler implements McpToolHandler {
    private final InterviewDefinitionRepository interviews;
    private final KnowledgeChunkRepository chunks;
    private final ObjectMapper mapper;

    public CitationToolHandler(InterviewDefinitionRepository interviews,
            KnowledgeChunkRepository chunks, ObjectMapper mapper) {
        this.interviews = interviews;
        this.chunks = chunks;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "knowledge"; }
    @Override public String toolName() { return "get_citation"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        var collectionId = InterviewContextToolHandler.parseId(arguments, "collectionId");
        var chunkId = InterviewContextToolHandler.parseId(arguments, "chunkId");
        if (!"INTERVIEW".equals(context.resourceType())) {
            throw new McpProtocolException(-32602, "Authorized interview is required");
        }
        interviews.findById(context.resourceId())
                .filter(value -> value.getOwnerSubject().equals(context.actorSubject()))
                .filter(value -> collectionId.equals(value.getKnowledgeCollectionId()))
                .orElseThrow(() -> new McpProtocolException(
                        -32003, "Knowledge collection is not bound to the interview"));
        var chunk = chunks.findById(chunkId)
                .filter(value -> collectionId.equals(value.getDocument().getCollection().getId()))
                .orElseThrow(() -> new McpProtocolException(
                        -32003, "Citation is not in the bound collection"));
        var output = mapper.createObjectNode();
        output.put("chunkId", chunk.getId().toString());
        output.put("documentId", chunk.getDocument().getId().toString());
        output.put("fileName", chunk.getDocument().getFileName());
        output.put("chunkIndex", chunk.getIndex());
        output.put("content", chunk.getContent());
        return output;
    }
}
