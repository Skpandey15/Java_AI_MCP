package com.onlineinterview.profile.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.onlineinterview.profile.application.ProfileService;
import com.onlineinterview.profile.domain.UserProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.onlineinterview.shared.security.SecurityConfig;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void rejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void candidateRegistrationAlwaysCreatesCandidateProfile() throws Exception {
        var candidate = UserProfile.registerCandidate("subject-1", "candidate@example.com", "Candidate");
        when(profileService.registerCandidate(
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(candidate);

        mockMvc.perform(post("/api/v1/profiles/registration-complete")
                        .with(jwt().jwt(token -> token
                                .subject("subject-1")
                                .claim("tenant_id", "demo")
                                .claim("email", "candidate@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Candidate\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
    }

    @Test
    void returnsExistingProfile() throws Exception {
        var candidate = UserProfile.registerCandidate("subject-1", "candidate@example.com", "Candidate");
        when(profileService.findBySubject("subject-1")).thenReturn(Optional.of(candidate));

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject("subject-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("candidate@example.com"));
    }
}
