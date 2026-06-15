package com.cargotrack.shipment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ShipmentStatus {
    PLANNED,
    LOADING,
    IN_TRANSIT,
    COMPLETED,
    CANCELLED;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED = Map.of(
            PLANNED, EnumSet.of(LOADING, CANCELLED),
            LOADING, EnumSet.of(IN_TRANSIT, CANCELLED),
            IN_TRANSIT, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(ShipmentStatus.class),
            CANCELLED, EnumSet.noneOf(ShipmentStatus.class));

    public boolean canTransitionTo(ShipmentStatus target) {
        if (target == null) {
            return false;
        }
        return ALLOWED.get(this).contains(target);
    }
}
