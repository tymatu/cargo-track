package com.cargotrack.user;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.EmailNormalizer;
import com.cargotrack.common.PageResponse;
import com.cargotrack.live.UserWebSocketSessionsInvalidatedEvent;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.user.dto.ChangeUserRoleRequest;
import com.cargotrack.user.dto.CreateEmployeeRequest;
import com.cargotrack.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final EnumSet<Role> EMPLOYEE_ROLES =
            EnumSet.of(Role.DRIVER, Role.DISPATCHER);
    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ShipmentRepository shipmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> findAll(
            Role role, UserStatus status, String search, Pageable pageable) {
        Specification<User> specification = (root, query, cb) -> cb.conjunction();
        if (role != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), term),
                    cb.like(cb.lower(root.get("firstName")), term),
                    cb.like(cb.lower(root.get("lastName")), term)));
        }
        return PageResponse.of(userRepository.findAll(specification, pageable)
                .map(userMapper::toDto));
    }

    @Transactional
    @Auditable(action = AuditAction.EMPLOYEE_CREATED, entityType = "User")
    public UserDto createEmployee(CreateEmployeeRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (!EMPLOYEE_ROLES.contains(request.role())) {
            throw ApiException.badRequest("Разрешены только роли DRIVER и DISPATCHER");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email уже зарегистрирован");
        }
        if (!warehouseRepository.existsById(request.warehouseId())) {
            throw ApiException.notFound("Склад не найден: " + request.warehouseId());
        }

        User employee = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .warehouseId(request.warehouseId())
                .build());
        return userMapper.toDto(employee);
    }

    @Transactional
    @Auditable(action = AuditAction.USER_BLOCKED, entityType = "User")
    public UserDto block(Long userId, Long actorId) {
        if (userId.equals(actorId)) {
            throw ApiException.badRequest("Нельзя заблокировать собственную учётную запись");
        }
        User user = loadLocked(userId);
        if (user.getStatus() == UserStatus.BLOCKED) {
            return userMapper.toDto(user);
        }
        requireNoActiveShipment(user);
        user.setStatus(UserStatus.BLOCKED);
        refreshTokenRepository.revokeAllByUserId(userId);
        eventPublisher.publishEvent(new UserWebSocketSessionsInvalidatedEvent(userId));
        return userMapper.toDto(user);
    }

    @Transactional
    @Auditable(action = AuditAction.USER_UNBLOCKED, entityType = "User")
    public UserDto unblock(Long userId) {
        User user = loadLocked(userId);
        user.setStatus(UserStatus.ACTIVE);
        return userMapper.toDto(user);
    }

    @Transactional
    @Auditable(action = AuditAction.USER_ROLE_CHANGED, entityType = "User")
    public UserDto changeRole(Long userId, ChangeUserRoleRequest request, Long actorId) {
        if (userId.equals(actorId)) {
            throw ApiException.badRequest("Нельзя изменить собственную роль");
        }
        User user = loadLocked(userId);
        requireNoActiveShipment(user);
        Long warehouseId = request.warehouseId();
        if (EMPLOYEE_ROLES.contains(request.role())) {
            if (warehouseId == null || !warehouseRepository.existsById(warehouseId)) {
                throw ApiException.badRequest("Для сотрудника требуется существующий склад");
            }
        } else {
            warehouseId = null;
        }
        user.setRole(request.role());
        user.setWarehouseId(warehouseId);
        refreshTokenRepository.revokeAllByUserId(userId);
        eventPublisher.publishEvent(new UserWebSocketSessionsInvalidatedEvent(userId));
        return userMapper.toDto(user);
    }

    private User loadLocked(Long userId) {
        return userRepository.findLockedById(userId)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    private void requireNoActiveShipment(User user) {
        if (user.getRole() == Role.DRIVER
                && shipmentRepository.existsByDriverIdAndStatusIn(
                        user.getId(), ACTIVE_SHIPMENT_STATUSES)) {
            throw ApiException.conflict(
                    "У водителя есть активный рейс");
        }
    }
}
