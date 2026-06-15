package com.cargotrack.routing;

import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentParcelRepository;
import com.cargotrack.parcel.ParcelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackingMapService {

    private final ShipmentParcelRepository shipmentParcelRepository;

    public TrackingMapDto findForParcel(Long parcelId) {
        return shipmentParcelRepository.findFirstByParcelIdOrderByLoadedAtDesc(parcelId)
                .map(link -> {
                    Shipment shipment = link.getShipment();
                    return new TrackingMapDto(
                            shipment.getId(),
                            shipment.getTruck().getId(),
                            RouteDto.from(shipment.getRoute()),
                            TruckPositionDto.from(shipment.getTruck(), shipment.getRoute()));
                })
                .orElse(null);
    }

    public PublicTrackingMapDto findPublicForParcel(Long parcelId, ParcelStatus status) {
        TrackingMapDto tracking = findForParcel(parcelId);
        if (tracking == null) {
            return new PublicTrackingMapDto(fallbackProgress(status), null);
        }
        TruckPositionDto position = tracking.position();
        Integer progressPercent = position == null
                ? fallbackProgress(status)
                : RouteMath.progressPercent(
                        tracking.route() == null ? null : tracking.route().geometry(),
                        position.latitude().doubleValue(),
                        position.longitude().doubleValue());
        return new PublicTrackingMapDto(progressPercent, position == null ? null : position.recordedAt());
    }

    private int fallbackProgress(ParcelStatus status) {
        return switch (status) {
            case CREATED, CANCELLED -> 0;
            case ACCEPTED_AT_ORIGIN -> 10;
            case LOADED -> 25;
            case IN_TRANSIT -> 50;
            case ARRIVED_AT_DESTINATION -> 90;
            case DELIVERED -> 100;
        };
    }
}
