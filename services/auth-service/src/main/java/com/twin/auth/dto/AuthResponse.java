package com.twin.auth.dto;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String username,
        Set<String> roles
) {
    public static AuthResponse of(String access, String refresh, String username, Set<String> roles) {
        return new AuthResponse(access, refresh, "Bearer", username, roles);
    }
}
