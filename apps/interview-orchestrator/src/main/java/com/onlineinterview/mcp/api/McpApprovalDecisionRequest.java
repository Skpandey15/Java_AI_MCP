package com.onlineinterview.mcp.api;

import jakarta.validation.constraints.NotNull;

public record McpApprovalDecisionRequest(@NotNull Boolean approved) {
}
