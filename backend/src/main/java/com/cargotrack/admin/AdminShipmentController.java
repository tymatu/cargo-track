package com.cargotrack.admin;

import com.cargotrack.common.PageResponse;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.shipment.dto.ShipmentDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shipments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin shipments", description = "Filtered shipment overview for administrators")
@SecurityRequirement(name = "bearerAuth")
public class AdminShipmentController {

    private final AdminShipmentService service;

    @GetMapping
    public PageResponse<ShipmentDto> findAll(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long driverId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.findAll(status, warehouseId, driverId, pageable);
    }
}
