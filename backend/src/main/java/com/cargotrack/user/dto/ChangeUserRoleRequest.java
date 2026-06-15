package com.cargotrack.user.dto;

import com.cargotrack.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(
        @NotNull Role role,
        Long warehouseId
) {
}
