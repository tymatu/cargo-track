CREATE INDEX ix_parcels_created_at ON parcels (created_at DESC);
CREATE INDEX ix_shipments_status_arrived ON shipments (status, arrived_at DESC);
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_trucks_status ON trucks (status);
CREATE INDEX ix_audit_created_at ON audit_log (created_at DESC);
