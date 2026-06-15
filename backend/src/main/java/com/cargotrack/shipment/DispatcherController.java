package com.cargotrack.shipment;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.PageResponse;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.dto.ParcelDto;
import com.cargotrack.shipment.dto.CreateShipmentRequest;
import com.cargotrack.shipment.dto.LoadParcelsRequest;
import com.cargotrack.shipment.dto.ShipmentDto;
import com.cargotrack.truck.TruckDto;
import com.cargotrack.user.UserDto;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dispatcher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DISPATCHER')")
@Tag(name = "Dispatcher", description = "Warehouse parcel intake and shipment planning")
@SecurityRequirement(name = "bearerAuth")
public class DispatcherController {

    private final DispatcherService dispatcherService;

    @GetMapping("/parcels")
    public PageResponse<ParcelDto> parcels(
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) Long originWarehouseId,
            @RequestParam(required = false) Long destinationWarehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.findParcels(
                principal, status, originWarehouseId, destinationWarehouseId, pageable);
    }

    @PostMapping("/parcels/{id}/accept")
    public ParcelDto accept(@PathVariable Long id,
                            @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.acceptParcel(id, principal);
    }

    @PostMapping("/parcels/{id}/deliver")
    public ParcelDto deliver(@PathVariable Long id,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.deliverParcel(id, principal);
    }

    @GetMapping("/trucks")
    public List<TruckDto> trucks(@AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.findAvailableTrucks(principal);
    }

    @GetMapping("/drivers")
    public List<UserDto> drivers(@AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.findAvailableDrivers(principal);
    }

    @PostMapping("/shipments")
    public ResponseEntity<ShipmentDto> createShipment(
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dispatcherService.createShipment(request, principal));
    }

    @PostMapping("/shipments/{id}/parcels")
    public ShipmentDto loadParcels(
            @PathVariable Long id,
            @Valid @RequestBody LoadParcelsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.loadParcels(id, request, principal);
    }

    @DeleteMapping("/shipments/{id}/parcels/{parcelId}")
    public ShipmentDto removeParcel(
            @PathVariable Long id,
            @PathVariable Long parcelId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.removeParcel(id, parcelId, principal);
    }

    @GetMapping("/shipments")
    public PageResponse<ShipmentDto> shipments(
            @RequestParam(required = false) ShipmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.findShipments(principal, status, pageable);
    }

    @GetMapping("/shipments/{id}")
    public ShipmentDto shipment(@PathVariable Long id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return dispatcherService.findShipment(id, principal);
    }
}
