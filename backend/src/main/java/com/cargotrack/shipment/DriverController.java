package com.cargotrack.shipment;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.PageResponse;
import com.cargotrack.shipment.dto.ShipmentDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver", description = "Assigned shipments, departure and arrival")
@SecurityRequirement(name = "bearerAuth")
public class DriverController {

    private final DriverService driverService;

    @GetMapping("/shipments/my")
    public PageResponse<ShipmentDto> myShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return driverService.findMy(principal, status, pageable);
    }

    @GetMapping("/shipments/{id}")
    @PreAuthorize("@shipmentSecurity.isAssignedDriver(#id, authentication)")
    public ShipmentDto shipment(@PathVariable Long id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return driverService.findShipment(id, principal);
    }

    @PostMapping("/shipments/{id}/depart")
    @PreAuthorize("@shipmentSecurity.isAssignedDriver(#id, authentication)")
    public ShipmentDto depart(@PathVariable Long id,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return driverService.depart(id, principal);
    }

    @PostMapping("/shipments/{id}/arrive")
    @PreAuthorize("@shipmentSecurity.isAssignedDriver(#id, authentication)")
    public ShipmentDto arrive(@PathVariable Long id,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return driverService.arrive(id, principal);
    }
}
