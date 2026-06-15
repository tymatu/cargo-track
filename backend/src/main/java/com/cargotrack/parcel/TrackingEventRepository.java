package com.cargotrack.parcel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByParcelIdOrderByCreatedAtAsc(Long parcelId);

    @EntityGraph(attributePaths = {"parcel", "warehouse"})
    Optional<TrackingEvent> findFirstByParcelIdOrderByCreatedAtDesc(Long parcelId);
}
