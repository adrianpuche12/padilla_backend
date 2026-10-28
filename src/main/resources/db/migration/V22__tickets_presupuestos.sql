-- =====================================================================
-- V22 (PRO-051 / PRO-053 / PRO-054, 2026-10-28): tickets completos.
-- - Metadata del modo de carga (Modos de Carga de Incidencias §9).
-- - Fecha de visita acordada y costo final del trabajo.
-- - Presupuestos del proveedor con aprobacion (y escalamiento por monto).
-- =====================================================================
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS modo_carga varchar(20) NOT NULL DEFAULT 'FORMULARIO';
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS tiempo_carga_segundos integer;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS dispositivo varchar(20);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS scheduled_at timestamptz;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS costo_final numeric(14,2);
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_modo_carga_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_modo_carga_check CHECK (modo_carga IN ('FORMULARIO', 'VOZ_GUIADA', 'ASISTENTE_IA'));

CREATE TABLE IF NOT EXISTS budgets (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id     uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    ticket_id     uuid NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    provider_id   uuid NOT NULL REFERENCES users(id),
    monto         numeric(14,2) NOT NULL CHECK (monto > 0),
    detalle       text NOT NULL,
    status        varchar(15) NOT NULL DEFAULT 'PENDIENTE',
    requiere_manager boolean NOT NULL DEFAULT FALSE,
    decidido_por  uuid REFERENCES users(id),
    decidido_at   timestamptz,
    motivo        text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT budgets_status_check CHECK (status IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'))
);
CREATE INDEX IF NOT EXISTS idx_budgets_ticket ON budgets (ticket_id);
CREATE INDEX IF NOT EXISTS idx_budgets_agency ON budgets (agency_id);

ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
ALTER TABLE budgets FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON budgets;
CREATE POLICY agency_isolation ON budgets
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());
GRANT SELECT, INSERT, UPDATE, DELETE ON budgets TO propero_app;
