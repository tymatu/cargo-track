package com.cargotrack.warehouse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Справочник складов — нужен форме создания посылки. CRUD для админа — Фаза 8. */
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    @GetMapping
    public List<WarehouseDto> findAll() {
        return warehouseMapper.toDtos(warehouseRepository.findAll(Sort.by("city")));
    }
}
