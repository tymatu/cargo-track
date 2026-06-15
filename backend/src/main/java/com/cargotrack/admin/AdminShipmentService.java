package com.cargotrack.admin;

import com.cargotrack.common.PageResponse;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentMapper;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.shipment.dto.ShipmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentMapper shipmentMapper;

    @Transactional(readOnly = true)
    public PageResponse<ShipmentDto> findAll(
            ShipmentStatus status,
            Long warehouseId,
            Long driverId,
            Pageable pageable) {
        Specification<Shipment> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (warehouseId != null) {
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("originWarehouse").get("id"), warehouseId),
                    cb.equal(root.get("destinationWarehouse").get("id"), warehouseId)));
        }
        if (driverId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("driver").get("id"), driverId));
        }
        Page<Shipment> idPage = shipmentRepository.findAll(specification, pageable);
        if (idPage.isEmpty()) {
            return PageResponse.of(idPage.map(shipmentMapper::toDto));
        }

        Map<Long, Shipment> detailedById = shipmentRepository
                .findDetailedByIdIn(idPage.map(Shipment::getId).getContent())
                .stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));
        var content = idPage.getContent().stream()
                .map(shipment -> detailedById.get(shipment.getId()))
                .map(shipmentMapper::toDto)
                .toList();

        return PageResponse.of(new PageImpl<>(content, pageable, idPage.getTotalElements()));
    }
}
