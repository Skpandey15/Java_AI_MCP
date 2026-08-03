package com.onlineinterview.platform.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.onlineinterview.shared.security.SecurityConfig;

@WebMvcTest(PlatformHealthController.class)
@Import(SecurityConfig.class)
class PlatformHealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsServiceHealth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", is("interview-orchestrator")))
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
