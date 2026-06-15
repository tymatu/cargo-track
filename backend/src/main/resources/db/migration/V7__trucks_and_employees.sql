-- Phase 4: fleet and demo operational employees.

CREATE TABLE trucks (
    id                BIGSERIAL PRIMARY KEY,
    plate_number      VARCHAR(20) NOT NULL,
    model             VARCHAR(100),
    capacity_kg       NUMERIC(10, 2) NOT NULL CHECK (capacity_kg > 0),
    status            VARCHAR(20) NOT NULL,
    home_warehouse_id BIGINT NOT NULL REFERENCES warehouses (id),
    current_lat       NUMERIC(9, 6),
    current_lng       NUMERIC(9, 6),
    last_position_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    updated_by        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ux_trucks_plate_number UNIQUE (plate_number)
);

CREATE INDEX ix_trucks_home_status ON trucks (home_warehouse_id, status);

INSERT INTO trucks (plate_number, model, capacity_kg, status, home_warehouse_id)
SELECT seed.plate_number, seed.model, seed.capacity_kg, 'IDLE', w.id
FROM (
    VALUES
        ('CT-PRG-01', 'Volvo FH', 12000.00, 'Praha-1'),
        ('CT-PRG-02', 'Mercedes Actros', 8000.00, 'Praha-1'),
        ('CT-BRN-01', 'DAF XF', 10000.00, 'Brno-1'),
        ('CT-OST-01', 'Scania R', 9000.00, 'Ostrava-1')
) AS seed(plate_number, model, capacity_kg, warehouse_name)
JOIN warehouses w ON w.name = seed.warehouse_name
ON CONFLICT (plate_number) DO NOTHING;

-- Demo password for all seeded employees: CargoTrack123!
INSERT INTO users (
    email, password_hash, first_name, last_name, phone, role, status, warehouse_id
)
SELECT seed.email,
       '$2a$10$RLhuGlOC8GNULRHlmUry4.G91.f/.yFmUJSMJ2/g.ChPTaoadq0YC',
       seed.first_name,
       seed.last_name,
       seed.phone,
       seed.role,
       'ACTIVE',
       w.id
FROM (
    VALUES
        ('dispatcher.prague@cargotrack.local', 'Pavel', 'Dispatcher', '+420700100101', 'DISPATCHER', 'Praha-1'),
        ('driver.prague@cargotrack.local', 'David', 'Driver', '+420700100102', 'DRIVER', 'Praha-1'),
        ('driver2.prague@cargotrack.local', 'Daniel', 'Driver', '+420700100103', 'DRIVER', 'Praha-1'),
        ('dispatcher.brno@cargotrack.local', 'Barbora', 'Dispatcher', '+420700200101', 'DISPATCHER', 'Brno-1'),
        ('driver.brno@cargotrack.local', 'Boris', 'Driver', '+420700200102', 'DRIVER', 'Brno-1')
) AS seed(email, first_name, last_name, phone, role, warehouse_name)
JOIN warehouses w ON w.name = seed.warehouse_name
ON CONFLICT (email) DO NOTHING;
