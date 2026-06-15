package com.cargotrack.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByWarehouseId(Long warehouseId);

    long countByStatus(UserStatus status);

    long countByRole(Role role);

    List<User> findByRoleAndWarehouseIdAndStatusOrderByLastNameAscFirstNameAsc(
            Role role, Long warehouseId, UserStatus status);

    List<User> findByRoleAndStatusOrderByLastNameAscFirstNameAsc(
            Role role, UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findLockedById(@Param("id") Long id);
}
