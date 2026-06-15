package com.cargotrack.live;

import com.cargotrack.admin.AdminDashboardService;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelMapper;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.TrackingEvent;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.parcel.dto.TrackingEventDto;
import com.cargotrack.routing.Route;
import com.cargotrack.routing.RoutePoint;
import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.Truck;
import com.cargotrack.truck.TruckStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveUpdatePublisherTest {

    private SimpMessagingTemplate messagingTemplate;
    private ShipmentRepository shipmentRepository;
    private TrackingEventRepository trackingEventRepository;
    private ParcelMapper parcelMapper;
    private AdminDashboardService adminDashboardService;
    private LiveUpdatePublisher publisher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        shipmentRepository = mock(ShipmentRepository.class);
        trackingEventRepository = mock(TrackingEventRepository.class);
        parcelMapper = mock(ParcelMapper.class);
        adminDashboardService = mock(AdminDashboardService.class);
        publisher = new LiveUpdatePublisher(
                messagingTemplate,
                shipmentRepository,
                trackingEventRepository,
                parcelMapper,
                adminDashboardService);
    }

    @Test
    void publishesTruckPositionToSdpTopicAndFleetSnapshot() {
        Truck truck = Truck.builder()
                .plateNumber("CT-TEST-01")
                .status(TruckStatus.IN_TRANSIT)
                .currentLat(BigDecimal.valueOf(50.075538))
                .currentLng(BigDecimal.valueOf(14.4378))
                .lastPositionAt(Instant.parse("2026-06-14T12:00:00Z"))
                .build();
        truck.setId(99L);
        Route route = Route.builder()
                .geometry(List.of(
                        new RoutePoint(50.075538, 14.4378),
                        new RoutePoint(49.1951, 16.6068)))
                .build();
        Shipment shipment = Shipment.builder()
                .truck(truck)
                .status(ShipmentStatus.IN_TRANSIT)
                .route(route)
                .build();
        shipment.setId(42L);
        List<FleetPositionDto> fleetSnapshot = List.of(new FleetPositionDto(
                42L,
                99L,
                "CT-TEST-01",
                TruckStatus.IN_TRANSIT,
                ShipmentStatus.IN_TRANSIT,
                TruckPositionDto.from(truck, route)));

        when(shipmentRepository.findDetailedById(42L)).thenReturn(Optional.of(shipment));
        when(adminDashboardService.fleet()).thenReturn(fleetSnapshot);

        publisher.publishTruckPosition(new TruckPositionChangedEvent(42L));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/trucks/99/position"),
                any(TruckPositionDto.class));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/shipments/42/position"),
                any(ShipmentLiveUpdateDto.class));
        verify(messagingTemplate).convertAndSend("/topic/admin/fleet", fleetSnapshot);
    }

    @Test
    void publishesParcelEventsByIdAndTrackingNumber() {
        Parcel parcel = Parcel.builder()
                .trackingNumber("CT-DEMO00001")
                .status(ParcelStatus.IN_TRANSIT)
                .build();
        parcel.setId(7L);
        TrackingEvent event = TrackingEvent.builder()
                .parcel(parcel)
                .status(ParcelStatus.IN_TRANSIT)
                .description("In transit")
                .build();
        TrackingEventDto eventDto = new TrackingEventDto(
                ParcelStatus.IN_TRANSIT,
                "In transit",
                null,
                Instant.parse("2026-06-14T12:00:00Z"));

        when(trackingEventRepository.findFirstByParcelIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(event));
        when(parcelMapper.toEventDto(event)).thenReturn(eventDto);

        publisher.publishParcelEvent(new ParcelStatusChangedEvent(7L));

        verify(messagingTemplate).convertAndSend(
                "/topic/parcels/7/events",
                new ParcelLiveUpdateDto(7L, ParcelStatus.IN_TRANSIT, eventDto));
        verify(messagingTemplate).convertAndSend(
                "/topic/parcels/CT-DEMO00001/events",
                new ParcelLiveUpdateDto(7L, ParcelStatus.IN_TRANSIT, eventDto));
    }
}
