package com.cargotrack.shipment;

import com.cargotrack.routing.RouteDto;
import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.dto.ShipmentDto;
import com.cargotrack.shipment.dto.ShipmentParcelDto;
import com.cargotrack.truck.TruckDto;
import com.cargotrack.user.UserMapper;
import com.cargotrack.warehouse.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShipmentMapper {

    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;

    public ShipmentDto toDto(Shipment shipment) {
        List<ShipmentParcelDto> parcels = shipment.getParcelLinks().stream()
                .map(link -> new ShipmentParcelDto(
                        link.getParcel().getId(),
                        link.getParcel().getTrackingNumber(),
                        link.getParcel().getStatus(),
                        link.getParcel().getWeightKg(),
                        link.getLoadedAt()))
                .sorted(Comparator.comparing(
                        ShipmentParcelDto::loadedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new ShipmentDto(
                shipment.getId(),
                shipment.getStatus(),
                TruckDto.from(shipment.getTruck()),
                userMapper.toDto(shipment.getDriver()),
                warehouseMapper.toDto(shipment.getOriginWarehouse()),
                warehouseMapper.toDto(shipment.getDestinationWarehouse()),
                shipment.getPlannedDepartureAt(),
                shipment.getDepartedAt(),
                shipment.getArrivedAt(),
                loadedWeight(shipment),
                parcels,
                RouteDto.from(shipment.getRoute()),
                TruckPositionDto.from(shipment.getTruck(), shipment.getRoute()),
                shipment.getCreatedAt());
    }

    public BigDecimal loadedWeight(Shipment shipment) {
        return shipment.getParcelLinks().stream()
                .map(link -> link.getParcel().getWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
