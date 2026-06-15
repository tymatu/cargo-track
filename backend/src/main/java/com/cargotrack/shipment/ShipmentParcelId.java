package com.cargotrack.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentParcelId implements Serializable {

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "parcel_id")
    private Long parcelId;
}
