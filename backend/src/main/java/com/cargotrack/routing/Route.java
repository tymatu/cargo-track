package com.cargotrack.routing;

import com.cargotrack.common.AuditableEntity;
import com.cargotrack.shipment.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, unique = true)
    private Shipment shipment;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "origin_latitude", precision = 10, scale = 6)
    private BigDecimal originLatitude;

    @Column(name = "origin_longitude", precision = 10, scale = 6)
    private BigDecimal originLongitude;

    @Column(name = "destination_latitude", precision = 10, scale = 6)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 10, scale = 6)
    private BigDecimal destinationLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<RoutePoint> geometry = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteSource source;
}
