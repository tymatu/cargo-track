package com.cargotrack.user;

public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Role role,
        UserStatus status
) {
}
