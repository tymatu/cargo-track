package com.cargotrack.routing;

import java.time.Instant;

public record PublicTrackingMapDto(
        Integer progressPercent,
        Instant updatedAt
) {
}
