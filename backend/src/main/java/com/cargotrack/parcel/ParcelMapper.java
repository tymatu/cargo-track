package com.cargotrack.parcel;

import com.cargotrack.parcel.dto.ParcelDto;
import com.cargotrack.parcel.dto.TrackingEventDto;
import com.cargotrack.warehouse.WarehouseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = WarehouseMapper.class)
public interface ParcelMapper {

    ParcelDto toDto(Parcel parcel);

    @Mapping(target = "warehouseCity", source = "warehouse.city")
    TrackingEventDto toEventDto(TrackingEvent event);

    List<TrackingEventDto> toEventDtos(List<TrackingEvent> events);
}
