package com.cargotrack.shipment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStatusTest {

    @Test
    void allowedTransitionsMatchSdp() {
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(ShipmentStatus.LOADING)).isTrue();
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.LOADING.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.LOADING.canTransitionTo(ShipmentStatus.CANCELLED)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.COMPLETED)).isTrue();
    }

    @Test
    void forbiddenAndTerminalTransitionsAreRejected() {
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(null)).isFalse();
        assertThat(ShipmentStatus.PLANNED.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isFalse();
        assertThat(ShipmentStatus.LOADING.canTransitionTo(ShipmentStatus.COMPLETED)).isFalse();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.CANCELLED)).isFalse();
        for (ShipmentStatus target : ShipmentStatus.values()) {
            assertThat(ShipmentStatus.COMPLETED.canTransitionTo(target)).isFalse();
            assertThat(ShipmentStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }
}
