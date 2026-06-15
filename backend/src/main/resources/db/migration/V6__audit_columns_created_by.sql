-- Phase 3: JPA auditing columns for every existing business entity.

ALTER TABLE users
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT,
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE warehouses
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT,
    ADD CONSTRAINT fk_warehouses_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_warehouses_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT,
    ADD CONSTRAINT fk_refresh_tokens_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_refresh_tokens_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE tracking_events
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_by BIGINT,
    ADD CONSTRAINT fk_tracking_events_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
