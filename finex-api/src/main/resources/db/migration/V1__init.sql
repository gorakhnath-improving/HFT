-- Phase 1 placeholder migration: proves Flyway wiring against PostgreSQL.
-- Real domain tables (accounts, instruments, orders, trades, ledger, ...) land in Phase 2+.
CREATE TABLE schema_bootstrap_marker (
    id          SMALLINT PRIMARY KEY DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT schema_bootstrap_marker_singleton CHECK (id = 1)
);

INSERT INTO schema_bootstrap_marker (id) VALUES (1);
