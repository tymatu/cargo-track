package com.cargotrack.parcel;

import com.cargotrack.parcel.dto.PublicTrackingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Публичный трекинг по номеру — без авторизации, как у настоящих перевозчиков (SDP, 7.2). */
@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final ParcelService parcelService;

    @GetMapping("/{trackingNumber}")
    public PublicTrackingDto track(@PathVariable String trackingNumber) {
        return parcelService.trackPublic(trackingNumber);
    }
}
