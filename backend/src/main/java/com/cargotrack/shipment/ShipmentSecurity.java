package com.cargotrack.shipment;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("shipmentSecurity")
@RequiredArgsConstructor
public class ShipmentSecurity {

    private final ShipmentRepository shipmentRepository;

    public boolean isAssignedDriver(Long shipmentId, Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        return principal.role() == Role.ADMIN
                || shipmentRepository.existsByIdAndDriverId(shipmentId, principal.getId());
    }
}
