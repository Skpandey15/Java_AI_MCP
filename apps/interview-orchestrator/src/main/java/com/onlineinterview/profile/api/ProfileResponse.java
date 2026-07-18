package com.onlineinterview.profile.api;

import com.onlineinterview.profile.domain.UserProfile;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        String displayName,
        String role,
        String status) {
    static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getDisplayName(),
                profile.getRole().name(),
                profile.getStatus().name());
    }
}
