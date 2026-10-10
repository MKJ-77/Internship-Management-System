package com.mkj.InternHub.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String name,
        String email,
        String role
) {
}
