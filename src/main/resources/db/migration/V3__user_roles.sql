-- Roles, so there can be an admin console.
--
-- DEFAULT 'USER' is what backfills the rows that already exist; the column is
-- NOT NULL and the entity has no insertable default of its own, so without the
-- default this migration could not run against a populated table.
--
-- There are only two roles on purpose. Anything finer-grained is a permission
-- model, and this application has too few distinct actions to need one.
ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

-- The admin user list is "newest first", optionally filtered by role or by the
-- active flag. created_at carries the ordering; the other two are low
-- cardinality and are left to the scan.
CREATE INDEX users_created_idx ON users (created_at DESC);
