package com.cargotrack.auth.dto;

import com.cargotrack.user.UserDto;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        UserDto user
) {

    public AuthResponse toResponse() {
        return new AuthResponse(accessToken, user);
    }
}
