package com.cargotrack.warehouse;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WarehouseMapper {

    WarehouseDto toDto(Warehouse warehouse);

    List<WarehouseDto> toDtos(List<Warehouse> warehouses);
}
