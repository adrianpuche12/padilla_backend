-- =====================================================================
-- V23 (PRO-064, 2026-10-28): comunicados (broadcast, ARQ 4 Mod.4 tipo 4).
-- Cada comunicado genera una notificacion COMUNICADO para cada destinatario;
-- esta tabla guarda el historial.
-- =====================================================================
CREATE TABLE IF NOT EXISTS announcements (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id     uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    titulo        varchar(200) NOT NULL,
    mensaje       text NOT NULL,
    roles         varchar(100) NOT NULL,
    destinatarios integer NOT NULL DEFAULT 0,
    created_by    uuid REFERENCES users(id),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_announcements_agency ON announcements (agency_id, created_at DESC);
ALTER TABLE announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE announcements FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON announcements;
CREATE POLICY agency_isolation ON announcements
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());
GRANT SELECT, INSERT, UPDATE, DELETE ON announcements TO propero_app;
