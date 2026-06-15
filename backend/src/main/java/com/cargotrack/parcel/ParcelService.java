package com.cargotrack.parcel;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.GeoUtils;
import com.cargotrack.common.IllegalStateTransitionException;
import com.cargotrack.common.PageResponse;
import com.cargotrack.live.ParcelStatusChangedEvent;
import com.cargotrack.parcel.dto.CreateParcelRequest;
import com.cargotrack.parcel.dto.ParcelDetailDto;
import com.cargotrack.parcel.dto.ParcelDto;
import com.cargotrack.parcel.dto.PriceRequest;
import com.cargotrack.parcel.dto.PublicTrackingDto;
import com.cargotrack.routing.TrackingMapService;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.warehouse.Warehouse;
import com.cargotrack.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ParcelService {

    private static final String TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ParcelRepository parcelRepository;
    private final TrackingEventRepository eventRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final ParcelMapper parcelMapper;
    private final TrackingMapService trackingMapService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Auditable(action = AuditAction.PARCEL_CREATED, entityType = "Parcel")
    public ParcelDto create(CreateParcelRequest request, Long senderId) {
        if (request.originWarehouseId().equals(request.destinationWarehouseId())) {
            throw ApiException.badRequest("Склады отправления и назначения должны различаться");
        }
        Warehouse origin = loadWarehouse(request.originWarehouseId());
        Warehouse destination = loadWarehouse(request.destinationWarehouseId());
        User sender = userRepository.getReferenceById(senderId);

        PriceQuote quote = pricingService.quote(
                request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                distanceKm(origin, destination));

        Parcel parcel = parcelRepository.save(Parcel.builder()
                .trackingNumber(generateTrackingNumber())
                .sender(sender)
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .recipientEmail(request.recipientEmail())
                .originWarehouse(origin)
                .destinationWarehouse(destination)
                .weightKg(request.weightKg())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .declaredValue(request.declaredValue())
                .price(quote.price())
                .status(ParcelStatus.CREATED)
                .build());

        eventRepository.save(TrackingEvent.builder()
                .parcel(parcel)
                .status(ParcelStatus.CREATED)
                .description("Посылка создана, ожидает приёма на складе " + origin.getName())
                .warehouse(origin)
                .build());

        return parcelMapper.toDto(parcel);
    }

    @Transactional(readOnly = true)
    public PriceQuote calculatePrice(PriceRequest request) {
        if (request.originWarehouseId().equals(request.destinationWarehouseId())) {
            throw ApiException.badRequest("Склады отправления и назначения должны различаться");
        }
        Warehouse origin = loadWarehouse(request.originWarehouseId());
        Warehouse destination = loadWarehouse(request.destinationWarehouseId());
        return pricingService.quote(
                request.weightKg(), request.lengthCm(), request.widthCm(), request.heightCm(),
                distanceKm(origin, destination));
    }

    @Transactional(readOnly = true)
    public PageResponse<ParcelDto> findMy(Long senderId, ParcelStatus status, Pageable pageable) {
        var page = status == null
                ? parcelRepository.findBySenderId(senderId, pageable)
                : parcelRepository.findBySenderIdAndStatus(senderId, status, pageable);
        return PageResponse.of(page.map(parcelMapper::toDto));
    }

    @Transactional(readOnly = true)
    public ParcelDetailDto findDetail(Long parcelId) {
        Parcel parcel = loadParcel(parcelId);
        var events = parcelMapper.toEventDtos(eventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId));
        return new ParcelDetailDto(
                parcelMapper.toDto(parcel),
                events,
                trackingMapService.findForParcel(parcelId));
    }

    @Transactional
    @Auditable(action = AuditAction.PARCEL_CANCELLED, entityType = "Parcel")
    public ParcelDto cancel(Long parcelId, Long actorId) {
        Parcel parcel = loadParcel(parcelId);
        changeStatus(parcel, ParcelStatus.CANCELLED, "Посылка отменена отправителем", null, actorId);
        return parcelMapper.toDto(parcel);
    }

    @Transactional(readOnly = true)
    public PublicTrackingDto trackPublic(String trackingNumber) {
        String normalizedTrackingNumber = normalizeTrackingNumber(trackingNumber);
        Parcel parcel = parcelRepository.findByTrackingNumber(normalizedTrackingNumber)
                .orElseThrow(() -> ApiException.notFound("Посылка с таким номером не найдена"));
        var events = parcelMapper.toEventDtos(
                eventRepository.findByParcelIdOrderByCreatedAtAsc(parcel.getId()));
        return new PublicTrackingDto(
                parcel.getTrackingNumber(),
                parcel.getStatus(),
                parcel.getOriginWarehouse().getCity(),
                parcel.getDestinationWarehouse().getCity(),
                maskName(parcel.getRecipientName()),
                parcel.getCreatedAt(),
                events,
                trackingMapService.findPublicForParcel(parcel.getId(), parcel.getStatus()));
    }

    /**
     * Единственная точка смены статуса (SDP, Приложение A): валидирует переход,
     * пишет tracking_event. Аудит — Фаза 3, WS-уведомления — Фаза 7.
     */
    public void changeStatus(Parcel parcel, ParcelStatus target, String description,
                             Warehouse warehouse, Long actorId) {
        if (!parcel.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException("посылки", parcel.getStatus(), target);
        }
        parcel.setStatus(target);
        eventRepository.save(TrackingEvent.builder()
                .parcel(parcel)
                .status(target)
                .description(description)
                .warehouse(warehouse)
                .build());
        eventPublisher.publishEvent(new ParcelStatusChangedEvent(parcel.getId()));
    }

    private Parcel loadParcel(Long id) {
        return parcelRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Посылка не найдена"));
    }

    private Warehouse loadWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Склад не найден: " + id));
    }

    private double distanceKm(Warehouse origin, Warehouse destination) {
        return GeoUtils.haversineKm(
                origin.getLatitude().doubleValue(), origin.getLongitude().doubleValue(),
                destination.getLatitude().doubleValue(), destination.getLongitude().doubleValue());
    }

    private String generateTrackingNumber() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder("CT-");
            for (int i = 0; i < TRACKING_LENGTH; i++) {
                sb.append(TRACKING_ALPHABET.charAt(RANDOM.nextInt(TRACKING_ALPHABET.length())));
            }
            candidate = sb.toString();
        } while (parcelRepository.existsByTrackingNumber(candidate));
        return candidate;
    }

    private String normalizeTrackingNumber(String trackingNumber) {
        return trackingNumber == null
                ? ""
                : trackingNumber.trim().toUpperCase(Locale.ROOT);
    }

    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "***";
        }
        return fullName.charAt(0) + "***";
    }
}
