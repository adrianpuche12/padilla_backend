-- ============================================================
-- Módulo 1: Client Profiles — perfiles extendidos de clientes
-- Sprint 8 — Módulo Clientes
-- Ejecutar manualmente en cada schema antes del deploy:
--   psql -U padilla -d padilla-db -c "\i V7__add_client_profiles.sql"
-- Reemplazar el schema según el ambiente:
--   padilla_dev / padilla_staging / padilla
-- ============================================================

CREATE TABLE IF NOT EXISTS padilla_dev.client_profiles (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL UNIQUE REFERENCES padilla_dev.users(id) ON DELETE CASCADE,
    legacy_id       INTEGER,
    persona_type    VARCHAR(10)  NOT NULL DEFAULT 'FISICA',
    document_type   VARCHAR(20),
    document_number VARCHAR(50),
    cuit_cuil       VARCHAR(20),
    address         VARCHAR(255),
    city            VARCHAR(100),
    province        VARCHAR(100),
    iva_condition   VARCHAR(50),
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_client_profiles_user_id
    ON padilla_dev.client_profiles(user_id);
