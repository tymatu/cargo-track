package com.cargotrack.shipment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LoadParcelsRequest(
        @NotEmpty @Size(max = 200) List<@NotNull @Positive Long> parcelIds
) {
}
