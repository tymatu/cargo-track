package com.cargotrack.parcel;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.PageResponse;
import com.cargotrack.parcel.dto.CreateParcelRequest;
import com.cargotrack.parcel.dto.ParcelDetailDto;
import com.cargotrack.parcel.dto.ParcelDto;
import com.cargotrack.parcel.dto.PriceRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
@Tag(name = "Parcels", description = "Customer parcel creation, pricing and lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class ParcelController {

    private final ParcelService parcelService;

    /** Создание — только USER (матрица доступа, SDP 5.6). */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ParcelDto> create(@Valid @RequestBody CreateParcelRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        ParcelDto created = parcelService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/calculate-price")
    @PreAuthorize("hasRole('USER')")
    public PriceQuote calculatePrice(@Valid @RequestBody PriceRequest request) {
        return parcelService.calculatePrice(request);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public PageResponse<ParcelDto> my(@RequestParam(required = false) ParcelStatus status,
                                      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                      Pageable pageable,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return parcelService.findMy(principal.getId(), status, pageable);
    }

    /** Детали — владелец или DISPATCHER (ADMIN наследует через RoleHierarchy). Иначе IDOR → 403. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DISPATCHER') or @parcelSecurity.isOwner(#id, authentication)")
    public ParcelDetailDto detail(@PathVariable Long id) {
        return parcelService.findDetail(id);
    }

    /** Отмена — только владелец и только из статуса CREATED (иначе 409). */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@parcelSecurity.isOwner(#id, authentication)")
    public ParcelDto cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return parcelService.cancel(id, principal.getId());
    }
}
