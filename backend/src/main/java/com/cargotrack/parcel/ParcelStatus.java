package com.cargotrack.parcel;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Машина состояний посылки (SDP, Приложение A).
 * Любой переход вне таблицы — IllegalStateTransitionException → 409.
 */
public enum ParcelStatus {
    CREATED,
    ACCEPTED_AT_ORIGIN,
    LOADED,
    IN_TRANSIT,
    ARRIVED_AT_DESTINATION,
    DELIVERED,
    CANCELLED;

    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(CANCELLED, ACCEPTED_AT_ORIGIN),
            ACCEPTED_AT_ORIGIN, EnumSet.of(LOADED),
            LOADED, EnumSet.of(ACCEPTED_AT_ORIGIN, IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(ARRIVED_AT_DESTINATION),
            ARRIVED_AT_DESTINATION, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(ParcelStatus.class),   // терминальный
            CANCELLED, EnumSet.noneOf(ParcelStatus.class));  // терминальный

    public boolean canTransitionTo(ParcelStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
