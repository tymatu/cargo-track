package com.cargotrack.auth.dto;

import com.cargotrack.user.UserDto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserDto user
) {
}
