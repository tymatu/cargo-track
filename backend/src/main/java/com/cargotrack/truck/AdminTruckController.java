package com.cargotrack.truck;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/trucks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin trucks", description = "Fleet vehicle administration")
@SecurityRequirement(name = "bearerAuth")
public class AdminTruckController {

    private final AdminTruckService service;

    @GetMapping
    public List<TruckDto> findAll() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<TruckDto> create(@Valid @RequestBody TruckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public TruckDto update(@PathVariable Long id, @Valid @RequestBody TruckRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public TruckDto delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
