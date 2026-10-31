package org.unsch.securityapi.dto;

import org.unsch.securityapi.entity.AppUser;
import java.util.UUID;

public record UserResponse(UUID id, String email, String role) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
