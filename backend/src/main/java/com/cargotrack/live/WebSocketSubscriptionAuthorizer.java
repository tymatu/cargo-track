package com.cargotrack.live;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.shipment.ShipmentParcelRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionAuthorizer {

    private static final Pattern SHIPMENT_POSITION =
            Pattern.compile("^/topic/shipments/(\\d+)/position$");
    private static final Pattern TRUCK_POSITION =
            Pattern.compile("^/topic/trucks/(\\d+)/position$");
    private static final Pattern PARCEL_ID_EVENTS =
            Pattern.compile("^/topic/parcels/(\\d+)/events$");
    private static final Pattern PARCEL_TRACKING_EVENTS =
            Pattern.compile("^/topic/parcels/([A-Za-z0-9][A-Za-z0-9_-]*)/events$");
    private static final String ADMIN_FLEET = "/topic/admin/fleet";
    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentParcelRepository shipmentParcelRepository;
    private final ParcelRepository parcelRepository;

    public boolean canSubscribe(UserPrincipal principal, String destination) {
        if (principal == null || destination == null) {
            return false;
        }
        if (ADMIN_FLEET.equals(destination)) {
            return principal.role() == Role.ADMIN;
        }

        Matcher shipmentMatcher = SHIPMENT_POSITION.matcher(destination);
        if (shipmentMatcher.matches()) {
            return canViewShipment(principal, Long.valueOf(shipmentMatcher.group(1)));
        }

        Matcher truckMatcher = TRUCK_POSITION.matcher(destination);
        if (truckMatcher.matches()) {
            return canViewTruck(principal, Long.valueOf(truckMatcher.group(1)));
        }

        Matcher parcelMatcher = PARCEL_ID_EVENTS.matcher(destination);
        if (parcelMatcher.matches()) {
            return canViewParcel(principal, Long.valueOf(parcelMatcher.group(1)));
        }

        Matcher trackingMatcher = PARCEL_TRACKING_EVENTS.matcher(destination);
        if (trackingMatcher.matches()) {
            return canViewParcel(principal, trackingMatcher.group(1));
        }
        return false;
    }

    private boolean canViewShipment(UserPrincipal principal, Long shipmentId) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case DRIVER -> shipmentRepository.existsByIdAndDriverId(
                    shipmentId, principal.getId());
            case DISPATCHER -> principal.warehouseId() != null
                    && shipmentRepository.existsByIdAndOriginWarehouseId(
                            shipmentId, principal.warehouseId());
            case USER -> shipmentParcelRepository.existsByShipmentIdAndParcelSenderId(
                    shipmentId, principal.getId());
        };
    }

    private boolean canViewParcel(UserPrincipal principal, Long parcelId) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case DRIVER -> shipmentParcelRepository.existsByParcelIdAndShipmentDriverId(
                    parcelId, principal.getId());
            case DISPATCHER -> principal.warehouseId() != null
                    && parcelRepository.existsByIdAtWarehouse(
                            parcelId, principal.warehouseId());
            case USER -> parcelRepository.existsByIdAndSenderId(
                    parcelId, principal.getId());
        };
    }

    private boolean canViewTruck(UserPrincipal principal, Long truckId) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case DRIVER -> shipmentRepository.existsByTruckIdAndDriverIdAndStatusIn(
                    truckId, principal.getId(), ACTIVE_SHIPMENT_STATUSES);
            case DISPATCHER -> principal.warehouseId() != null
                    && shipmentRepository.existsByTruckIdAndOriginWarehouseIdAndStatusIn(
                            truckId, principal.warehouseId(), ACTIVE_SHIPMENT_STATUSES);
            case USER -> shipmentParcelRepository.existsByShipmentTruckIdAndParcelSenderIdAndShipmentStatusIn(
                    truckId, principal.getId(), ACTIVE_SHIPMENT_STATUSES);
        };
    }

    private boolean canViewParcel(UserPrincipal principal, String trackingNumber) {
        return switch (principal.role()) {
            case ADMIN -> true;
            case DRIVER -> shipmentParcelRepository.existsByParcelTrackingNumberAndShipmentDriverId(
                    trackingNumber, principal.getId());
            case DISPATCHER -> principal.warehouseId() != null
                    && parcelRepository.existsByTrackingNumberAtWarehouse(
                            trackingNumber, principal.warehouseId());
            case USER -> parcelRepository.existsByTrackingNumberAndSenderId(
                    trackingNumber, principal.getId());
        };
    }
}
