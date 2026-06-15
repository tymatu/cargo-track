package com.cargotrack.shipment;

import com.cargotrack.parcel.Parcel;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "shipment_parcels")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentParcel {

    @EmbeddedId
    private ShipmentParcelId id;

    @MapsId("shipmentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @MapsId("parcelId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @CreationTimestamp
    @Column(name = "loaded_at", nullable = false, updatable = false)
    private Instant loadedAt;
}
