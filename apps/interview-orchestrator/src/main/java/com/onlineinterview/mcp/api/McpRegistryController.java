package com.onlineinterview.mcp.api;

import com.onlineinterview.mcp.application.McpRegistryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp/registry")
@PreAuthorize("hasRole('INTERVIEWER')")
public class McpRegistryController {
    private final McpRegistryService registry;

    public McpRegistryController(McpRegistryService registry) { this.registry = registry; }

    @GetMapping
    public List<McpRegistryResponse> list() {
        return registry.listEnabled().stream().map(McpRegistryResponse::from).toList();
    }
}
