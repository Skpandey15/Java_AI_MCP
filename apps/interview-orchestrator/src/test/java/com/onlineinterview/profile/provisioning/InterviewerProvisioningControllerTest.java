package com.onlineinterview.profile.provisioning;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.onlineinterview.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InterviewerProvisioningController.class)
@Import(SecurityConfig.class)
class InterviewerProvisioningControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean InterviewerProvisioningService service;

    @Test
    void rejectsNonAdministrator() throws Exception {
        mockMvc.perform(request().with(jwt().jwt(j -> j.subject("interviewer"))
                        .authorities(createAuthorityList("ROLE_INTERVIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/admin/interviewers")
                        .with(jwt().jwt(j -> j.subject("admin"))
                                .authorities(createAuthorityList("ROLE_PLATFORM_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformAdministratorCanProvision() throws Exception {
        var operation = IdentityProvisioning.pending(
                "request-1", "tenant-a", "person@example.com", "Person", "admin");
        when(service.provision(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(operation);
        mockMvc.perform(request().with(jwt().jwt(j -> j.subject("admin"))
                        .authorities(createAuthorityList("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.email").value("person@example.com"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post("/api/v1/admin/interviewers")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON).content(body());
    }
    private static String body() {
        return "{\"tenantId\":\"tenant-a\",\"email\":\"person@example.com\",\"displayName\":\"Person\"}";
    }
}
