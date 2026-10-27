-- ============================================================
-- Módulo 1: Provider Specialty — especialidad para proveedores
-- Sprint 8 — Módulo Proveedores
-- Ejecutar manualmente en cada schema antes del deploy:
--   psql -U padilla -d padilla-db -c "\i V8__add_specialty_to_client_profiles.sql"
-- Reemplazar el schema según el ambiente:
--   padilla_dev / padilla_staging / padilla
-- ============================================================

ALTER TABLE padilla_dev.client_profiles
    ADD COLUMN IF NOT EXISTS specialty VARCHAR(30);
