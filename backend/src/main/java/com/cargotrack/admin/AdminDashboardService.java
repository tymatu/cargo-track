package com.cargotrack.admin;

import com.cargotrack.live.FleetPositionDto;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.truck.TruckStatus;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ParcelRepository parcelRepository;
    private final ShipmentRepository shipmentRepository;
    private final TruckRepository truckRepository;

    @Transactional(readOnly = true)
    public AdminDashboardDto stats(Instant from, Instant to) {
        var dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        var dayEnd = dayStart.plus(java.time.Duration.ofDays(1));
        Map<ParcelStatus, Long> parcelCounts = new EnumMap<>(ParcelStatus.class);
        for (ParcelStatus status : ParcelStatus.values()) {
            parcelCounts.put(status, 0L);
        }
        parcelRepository.countGroupedByStatus()
                .forEach(row -> parcelCounts.put(row.getStatus(), row.getTotal()));
        long activeShipments = EnumSet.of(
                        ShipmentStatus.PLANNED,
                        ShipmentStatus.LOADING,
                        ShipmentStatus.IN_TRANSIT)
                .stream()
                .mapToLong(shipmentRepository::countByStatus)
                .sum();
        BigDecimal revenue = parcelRepository.sumRevenueCreatedBetween(from, to);
        return new AdminDashboardDto(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                parcelRepository.count(),
                parcelCounts,
                revenue,
                from,
                to,
                shipmentRepository.count(),
                activeShipments,
                shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT),
                shipmentRepository.countByStatusAndArrivedAtBetween(
                        ShipmentStatus.COMPLETED, dayStart, dayEnd),
                truckRepository.count(),
                truckRepository.countByStatus(TruckStatus.IDLE),
                truckRepository.countByStatus(TruckStatus.IN_TRANSIT),
                truckRepository.countByStatus(TruckStatus.MAINTENANCE));
    }

    @Transactional(readOnly = true)
    public List<FleetPositionDto> fleet() {
        return shipmentRepository.findAllByStatus(ShipmentStatus.IN_TRANSIT).stream()
                .map(shipment -> new FleetPositionDto(
                        shipment.getId(),
                        shipment.getTruck().getId(),
                        shipment.getTruck().getPlateNumber(),
                        shipment.getTruck().getStatus(),
                        shipment.getStatus(),
                        TruckPositionDto.from(shipment.getTruck(), shipment.getRoute())))
                .toList();
    }
}
