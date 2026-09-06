package org.tavoo.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        boolean rememberMe
) {
}
