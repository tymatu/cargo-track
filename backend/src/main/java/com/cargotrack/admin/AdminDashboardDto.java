package com.cargotrack.admin;

import com.cargotrack.parcel.ParcelStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AdminDashboardDto(
        long usersTotal,
        long usersActive,
        long parcelsTotal,
        Map<ParcelStatus, Long> parcelsByStatus,
        BigDecimal revenue,
        Instant revenueFrom,
        Instant revenueTo,
        long shipmentsTotal,
        long shipmentsActive,
        long shipmentsInTransit,
        long shipmentsCompletedToday,
        long trucksTotal,
        long trucksIdle,
        long trucksInTransit,
        long trucksMaintenance
) {
}
