-- ============================================================
-- Módulo: Notificaciones Email — Primer Login
-- Sprint 5 — Feature: registro de usuario con email de bienvenida
-- Ejecutar manualmente en cada schema antes del deploy:
--   psql -U padilla -d padilla -c "\i V4__add_password_fields.sql"
-- Reemplazar el schema según el ambiente:
--   padilla_dev / padilla_staging / padilla
-- ============================================================

ALTER TABLE padilla_dev.users
    ADD COLUMN IF NOT EXISTS first_login               BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMPTZ;

-- Los usuarios existentes ya completaron su setup: marcarlos como first_login = false
UPDATE padilla_dev.users
SET first_login = FALSE
WHERE created_at < NOW();
