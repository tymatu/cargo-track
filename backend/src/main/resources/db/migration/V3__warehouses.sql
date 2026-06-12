-- Фаза 2: склады (SDP, раздел 4.2)

CREATE TABLE warehouses (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    latitude   NUMERIC(9, 6) NOT NULL,
    longitude  NUMERIC(9, 6) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- FK, отложенный из Фазы 1 (V2): привязка сотрудников к складу
ALTER TABLE users
    ADD CONSTRAINT fk_users_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

-- Сидер складов: реальные координаты городов Чехии и соседей
INSERT INTO warehouses (name, city, address, latitude, longitude) VALUES
    ('Praha-1',   'Praha',   'Logistická 1',  50.075538, 14.437800),
    ('Brno-1',    'Brno',    'Skladová 12',   49.195061, 16.606836),
    ('Ostrava-1', 'Ostrava','Nádražní 5',    49.820923, 18.262524),
    ('Plzeň-1',   'Plzeň',   'Průmyslová 8',  49.738431, 13.373637),
    ('Wien-1',    'Wien',    'Lagerstraße 3', 48.208174, 16.373819),
    ('Dresden-1', 'Dresden', 'Lagerplatz 7',  51.050409, 13.737262);
