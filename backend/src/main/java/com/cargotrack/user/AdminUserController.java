package com.cargotrack.user;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cargotrack.user.dto.ChangeUserRoleRequest;
import com.cargotrack.user.dto.CreateEmployeeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin users", description = "Employee creation, roles and account blocking")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<UserDto> findAll(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminUserService.findAll(role, status, search, pageable);
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createEmployee(request));
    }

    @PostMapping("/{id}/block")
    public UserDto block(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return adminUserService.block(id, principal.getId());
    }

    @PostMapping("/{id}/unblock")
    public UserDto unblock(@PathVariable Long id) {
        return adminUserService.unblock(id);
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeUserRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return adminUserService.changeRole(id, request, principal.getId());
    }
}
