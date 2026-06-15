package com.cargotrack.warehouse;

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
@RequestMapping("/api/v1/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin warehouses", description = "Warehouse administration")
@SecurityRequirement(name = "bearerAuth")
public class AdminWarehouseController {

    private final AdminWarehouseService service;

    @GetMapping
    public List<WarehouseDto> findAll() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<WarehouseDto> create(
            @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public WarehouseDto update(
            @PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public WarehouseDto delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
