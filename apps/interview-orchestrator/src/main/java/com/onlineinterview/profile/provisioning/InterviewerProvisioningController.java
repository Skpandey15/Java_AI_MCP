package com.onlineinterview.profile.provisioning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/interviewers")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class InterviewerProvisioningController {
    private final InterviewerProvisioningService service;
    public InterviewerProvisioningController(InterviewerProvisioningService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProvisioningResponse> provision(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProvisionInterviewerRequest request) {
        var result = service.provision(idempotencyKey, request.tenantId(), request.email(),
                request.displayName(), jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/v1/admin/interviewers/" + result.getId()))
                .body(ProvisioningResponse.from(result));
    }
}
