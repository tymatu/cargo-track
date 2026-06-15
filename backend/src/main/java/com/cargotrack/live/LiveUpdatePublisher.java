package com.cargotrack.live;

import com.cargotrack.admin.AdminDashboardService;
import com.cargotrack.parcel.ParcelMapper;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LiveUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final ParcelMapper parcelMapper;
    private final AdminDashboardService adminDashboardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishTruckPosition(TruckPositionChangedEvent event) {
        shipmentRepository.findDetailedById(event.shipmentId()).ifPresent(shipment -> {
            TruckPositionDto position = TruckPositionDto.from(
                    shipment.getTruck(), shipment.getRoute());
            messagingTemplate.convertAndSend(
                    "/topic/trucks/" + shipment.getTruck().getId() + "/position",
                    position);
            messagingTemplate.convertAndSend(
                    "/topic/shipments/" + shipment.getId() + "/position",
                    new ShipmentLiveUpdateDto(
                            shipment.getId(),
                            shipment.getStatus(),
                            shipment.getTruck().getStatus(),
                            position,
                            shipment.getArrivedAt()));
            messagingTemplate.convertAndSend("/topic/admin/fleet", adminDashboardService.fleet());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishParcelEvent(ParcelStatusChangedEvent event) {
        trackingEventRepository.findFirstByParcelIdOrderByCreatedAtDesc(event.parcelId())
                .ifPresent(trackingEvent -> {
                    ParcelLiveUpdateDto update = new ParcelLiveUpdateDto(
                            event.parcelId(),
                            trackingEvent.getStatus(),
                            parcelMapper.toEventDto(trackingEvent));
                    messagingTemplate.convertAndSend(
                            "/topic/parcels/" + event.parcelId() + "/events",
                            update);
                    messagingTemplate.convertAndSend(
                            "/topic/parcels/" + trackingEvent.getParcel().getTrackingNumber() + "/events",
                            update);
                });
    }
}
