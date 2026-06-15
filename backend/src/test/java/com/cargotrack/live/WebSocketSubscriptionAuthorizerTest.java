package com.cargotrack.live;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.shipment.ShipmentParcelRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.user.Role;
import com.cargotrack.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSubscriptionAuthorizerTest {

    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private ShipmentRepository shipmentRepository;
    private ShipmentParcelRepository shipmentParcelRepository;
    private ParcelRepository parcelRepository;
    private WebSocketSubscriptionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        shipmentRepository = mock(ShipmentRepository.class);
        shipmentParcelRepository = mock(ShipmentParcelRepository.class);
        parcelRepository = mock(ParcelRepository.class);
        authorizer = new WebSocketSubscriptionAuthorizer(
                shipmentRepository, shipmentParcelRepository, parcelRepository);
    }

    @Test
    void adminCanSubscribeToFleet_butUserCannot() {
        assertThat(authorizer.canSubscribe(
                principal(1L, Role.ADMIN, null), "/topic/admin/fleet")).isTrue();
        assertThat(authorizer.canSubscribe(
                principal(2L, Role.USER, null), "/topic/admin/fleet")).isFalse();
    }

    @Test
    void assignedDriverCanSubscribeToShipmentPosition() {
        when(shipmentRepository.existsByIdAndDriverId(42L, 7L)).thenReturn(true);
        when(shipmentRepository.existsByTruckIdAndDriverIdAndStatusIn(
                77L, 7L, ACTIVE_SHIPMENT_STATUSES)).thenReturn(true);

        assertThat(authorizer.canSubscribe(
                principal(7L, Role.DRIVER, 1L),
                "/topic/shipments/42/position")).isTrue();
        assertThat(authorizer.canSubscribe(
                principal(7L, Role.DRIVER, 1L),
                "/topic/trucks/77/position")).isTrue();
        assertThat(authorizer.canSubscribe(
                principal(8L, Role.DRIVER, 1L),
                "/topic/trucks/77/position")).isFalse();
    }

    @Test
    void parcelOwnerCanSubscribeToParcelEventsAndShipmentPosition() {
        when(parcelRepository.existsByIdAndSenderId(9L, 3L)).thenReturn(true);
        when(parcelRepository.existsByTrackingNumberAndSenderId("CT-DEMO00009", 3L))
                .thenReturn(true);
        when(shipmentParcelRepository.existsByShipmentIdAndParcelSenderId(12L, 3L))
                .thenReturn(true);
        when(shipmentParcelRepository.existsByShipmentTruckIdAndParcelSenderIdAndShipmentStatusIn(
                77L, 3L, ACTIVE_SHIPMENT_STATUSES))
                .thenReturn(true);
        UserPrincipal owner = principal(3L, Role.USER, null);

        assertThat(authorizer.canSubscribe(owner, "/topic/parcels/9/events")).isTrue();
        assertThat(authorizer.canSubscribe(owner, "/topic/parcels/CT-DEMO00009/events")).isTrue();
        assertThat(authorizer.canSubscribe(
                owner, "/topic/shipments/12/position")).isTrue();
        assertThat(authorizer.canSubscribe(
                owner, "/topic/trucks/77/position")).isTrue();
        assertThat(authorizer.canSubscribe(owner, "/topic/other")).isFalse();
    }

    private UserPrincipal principal(Long id, Role role, Long warehouseId) {
        return new UserPrincipal(
                id, "test@example.com", "hash", role, UserStatus.ACTIVE, warehouseId);
    }
}
