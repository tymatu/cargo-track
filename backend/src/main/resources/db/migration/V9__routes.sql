-- Phase 6: persisted road geometry for shipment simulation and map rendering.

CREATE TABLE routes (
    id            BIGSERIAL PRIMARY KEY,
    shipment_id   BIGINT NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    distance_km   NUMERIC(10, 2) NOT NULL CHECK (distance_km >= 0),
    duration_min  INTEGER NOT NULL CHECK (duration_min >= 0),
    geometry      JSONB NOT NULL,
    source        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    updated_by    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_routes_shipment UNIQUE (shipment_id)
);
