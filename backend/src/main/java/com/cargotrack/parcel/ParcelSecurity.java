package com.cargotrack.parcel;

import com.cargotrack.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Проверка владения ресурсом — защита от IDOR (SDP, раздел 5.5):
 * hasRole отвечает «кто ты», этот бин — «твоя ли это посылка».
 */
@Component("parcelSecurity")
@RequiredArgsConstructor
public class ParcelSecurity {

    private final ParcelRepository parcelRepository;

    public boolean isOwner(Long parcelId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        return parcelRepository.existsByIdAndSenderId(parcelId, principal.getId());
    }
}
