-- =====================================================================
-- V25 (PRO-073, 2026-10-28): log de auditoria (ARQ 7.3: SUPER_ADMIN y
-- MANAGER leen la auditoria). Quien hizo que, sobre que entidad y cuando.
-- Solo se inserta: la app no actualiza ni borra filas de auditoria.
-- =====================================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id   uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    actor_id    uuid,
    actor_rol   varchar(20),
    accion      varchar(60) NOT NULL,
    entidad     varchar(30) NOT NULL,
    entidad_id  uuid,
    detalle     text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_agency_fecha ON audit_log (agency_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entidad ON audit_log (entidad, entidad_id);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON audit_log;
CREATE POLICY agency_isolation ON audit_log
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());

-- inmutable para la app: solo SELECT e INSERT
REVOKE UPDATE, DELETE ON audit_log FROM propero_app;
GRANT SELECT, INSERT ON audit_log TO propero_app;
