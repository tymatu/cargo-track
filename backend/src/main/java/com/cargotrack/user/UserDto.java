package com.cargotrack.user;

import com.cargotrack.common.HasId;

public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Role role,
        UserStatus status,
        Long warehouseId
) implements HasId {
}
