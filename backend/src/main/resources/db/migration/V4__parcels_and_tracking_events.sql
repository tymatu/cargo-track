-- Фаза 2: посылки и история трекинга (SDP, разделы 4.2, 4.4)

CREATE TABLE parcels (
    id                       BIGSERIAL PRIMARY KEY,
    tracking_number          VARCHAR(20)  NOT NULL,
    sender_id                BIGINT       NOT NULL REFERENCES users (id),
    recipient_name           VARCHAR(200) NOT NULL,
    recipient_phone          VARCHAR(30)  NOT NULL,
    recipient_email          VARCHAR(255),
    origin_warehouse_id      BIGINT       NOT NULL REFERENCES warehouses (id),
    destination_warehouse_id BIGINT       NOT NULL REFERENCES warehouses (id),
    weight_kg                NUMERIC(8, 2) NOT NULL CHECK (weight_kg > 0),
    length_cm                NUMERIC(6, 1),
    width_cm                 NUMERIC(6, 1),
    height_cm                NUMERIC(6, 1),
    declared_value           NUMERIC(12, 2),
    price                    NUMERIC(12, 2) NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               BIGINT REFERENCES users (id),
    updated_by               BIGINT REFERENCES users (id),
    CONSTRAINT chk_parcel_warehouses CHECK (origin_warehouse_id <> destination_warehouse_id)
);

CREATE UNIQUE INDEX ux_parcels_tracking ON parcels (tracking_number);
CREATE INDEX ix_parcels_sender ON parcels (sender_id);
-- очередь диспетчера: посылки склада в заданном статусе
CREATE INDEX ix_parcels_status_origin ON parcels (status, origin_warehouse_id);

CREATE TABLE tracking_events (
    id           BIGSERIAL PRIMARY KEY,
    parcel_id    BIGINT      NOT NULL REFERENCES parcels (id),
    status       VARCHAR(30) NOT NULL,
    description  VARCHAR(255),
    warehouse_id BIGINT REFERENCES warehouses (id),
    created_by   BIGINT REFERENCES users (id), -- NULL = система/симулятор
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_tracking_events_parcel ON tracking_events (parcel_id, created_at);
