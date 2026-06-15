package com.cargotrack.parcel;

import org.junit.jupiter.api.Test;

import static com.cargotrack.parcel.ParcelStatus.ACCEPTED_AT_ORIGIN;
import static com.cargotrack.parcel.ParcelStatus.ARRIVED_AT_DESTINATION;
import static com.cargotrack.parcel.ParcelStatus.CANCELLED;
import static com.cargotrack.parcel.ParcelStatus.CREATED;
import static com.cargotrack.parcel.ParcelStatus.DELIVERED;
import static com.cargotrack.parcel.ParcelStatus.IN_TRANSIT;
import static com.cargotrack.parcel.ParcelStatus.LOADED;
import static org.assertj.core.api.Assertions.assertThat;

class ParcelStatusTest {

    @Test
    void allowedTransitionsMatchSdpAppendixA() {
        assertThat(CREATED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CREATED.canTransitionTo(ACCEPTED_AT_ORIGIN)).isTrue();
        assertThat(ACCEPTED_AT_ORIGIN.canTransitionTo(LOADED)).isTrue();
        assertThat(LOADED.canTransitionTo(ACCEPTED_AT_ORIGIN)).isTrue(); // убрали из рейса
        assertThat(LOADED.canTransitionTo(IN_TRANSIT)).isTrue();
        assertThat(IN_TRANSIT.canTransitionTo(ARRIVED_AT_DESTINATION)).isTrue();
        assertThat(ARRIVED_AT_DESTINATION.canTransitionTo(DELIVERED)).isTrue();
    }

    @Test
    void forbiddenTransitionsAreRejected() {
        assertThat(CREATED.canTransitionTo(null)).isFalse();
        assertThat(CREATED.canTransitionTo(IN_TRANSIT)).isFalse();
        assertThat(CREATED.canTransitionTo(DELIVERED)).isFalse();
        assertThat(ACCEPTED_AT_ORIGIN.canTransitionTo(CANCELLED)).isFalse(); // после приёма не отменить
        assertThat(IN_TRANSIT.canTransitionTo(LOADED)).isFalse();
    }

    @Test
    void terminalStatusesHaveNoTransitions() {
        for (ParcelStatus target : ParcelStatus.values()) {
            assertThat(DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
        }
    }
}
