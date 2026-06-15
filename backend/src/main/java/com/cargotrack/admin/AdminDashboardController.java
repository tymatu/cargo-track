package com.cargotrack.admin;

import com.cargotrack.live.FleetPositionDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin dashboard", description = "System aggregates and live fleet snapshot")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService service;

    @GetMapping({"/stats/dashboard", "/dashboard"})
    public AdminDashboardDto stats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant periodTo = to == null ? Instant.now() : to;
        Instant periodFrom = from == null ? periodTo.minus(Duration.ofDays(30)) : from;
        if (!periodFrom.isBefore(periodTo)) {
            throw com.cargotrack.common.ApiException.badRequest(
                    "Начало периода должно быть раньше его окончания");
        }
        return service.stats(periodFrom, periodTo);
    }

    @GetMapping({"/fleet", "/dashboard/fleet"})
    public List<FleetPositionDto> fleet() {
        return service.fleet();
    }
}
