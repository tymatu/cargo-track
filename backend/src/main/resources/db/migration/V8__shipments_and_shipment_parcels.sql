-- Phase 4: direct warehouse-to-warehouse shipments and their parcel manifest.

CREATE TABLE shipments (
    id                       BIGSERIAL PRIMARY KEY,
    truck_id                 BIGINT NOT NULL REFERENCES trucks (id),
    driver_id                BIGINT NOT NULL REFERENCES users (id),
    origin_warehouse_id      BIGINT NOT NULL REFERENCES warehouses (id),
    destination_warehouse_id BIGINT NOT NULL REFERENCES warehouses (id),
    status                   VARCHAR(20) NOT NULL,
    planned_departure_at     TIMESTAMPTZ,
    departed_at              TIMESTAMPTZ,
    arrived_at               TIMESTAMPTZ,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               BIGINT REFERENCES users (id) ON DELETE SET NULL,
    updated_by               BIGINT REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_shipment_warehouses CHECK (origin_warehouse_id <> destination_warehouse_id)
);

CREATE TABLE shipment_parcels (
    shipment_id BIGINT NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    parcel_id   BIGINT NOT NULL REFERENCES parcels (id),
    loaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shipment_id, parcel_id)
);

CREATE INDEX ix_shipments_origin_status ON shipments (origin_warehouse_id, status);
CREATE INDEX ix_shipments_driver_status ON shipments (driver_id, status);
CREATE INDEX ix_shipments_truck_status ON shipments (truck_id, status);
CREATE INDEX ix_shipment_parcels_parcel ON shipment_parcels (parcel_id);

CREATE UNIQUE INDEX ux_shipments_active_truck
    ON shipments (truck_id)
    WHERE status IN ('PLANNED', 'LOADING', 'IN_TRANSIT');

CREATE UNIQUE INDEX ux_shipments_active_driver
    ON shipments (driver_id)
    WHERE status IN ('PLANNED', 'LOADING', 'IN_TRANSIT');
