package com.onlineinterview.profile.provisioning;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProvisionInterviewerRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,79}") String tenantId,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String displayName) {}
