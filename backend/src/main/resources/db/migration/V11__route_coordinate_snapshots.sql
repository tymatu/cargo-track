-- Keep route cache safe when warehouse coordinates are edited.

ALTER TABLE routes
    ADD COLUMN origin_latitude NUMERIC(10, 6),
    ADD COLUMN origin_longitude NUMERIC(10, 6),
    ADD COLUMN destination_latitude NUMERIC(10, 6),
    ADD COLUMN destination_longitude NUMERIC(10, 6);

UPDATE routes r
SET origin_latitude = ow.latitude,
    origin_longitude = ow.longitude,
    destination_latitude = dw.latitude,
    destination_longitude = dw.longitude
FROM shipments s
JOIN warehouses ow ON ow.id = s.origin_warehouse_id
JOIN warehouses dw ON dw.id = s.destination_warehouse_id
WHERE s.id = r.shipment_id;
