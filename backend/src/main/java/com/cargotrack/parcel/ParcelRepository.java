package com.cargotrack.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    boolean existsByIdAndSenderId(Long id, Long senderId);

    boolean existsByTrackingNumber(String trackingNumber);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    Page<Parcel> findBySenderId(Long senderId, Pageable pageable);

    Page<Parcel> findBySenderIdAndStatus(Long senderId, ParcelStatus status, Pageable pageable);
}
