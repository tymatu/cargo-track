package com.cargotrack.parcel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByParcelIdOrderByCreatedAtAsc(Long parcelId);
}
