-- =====================================================================
-- V14 (PRO-012, 2026-10-27): estado CANCELADA para tickets.
-- Decision D4 (Saas/construccion/03-Plan): se mantienen los 5 estados del
-- MVP + CANCELADA (inquilino que se arrepiente, baja del inquilino).
-- El motivo de rechazo/cancelacion se guarda en rejection_reason.
-- =====================================================================
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_status_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_status_check CHECK (status IN
    ('ABIERTA', 'EN_PROCESO', 'RESUELTA', 'CERRADA', 'RECHAZADA', 'CANCELADA'));

CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets (status);
CREATE INDEX IF NOT EXISTS idx_tickets_property ON tickets (property_id);
CREATE INDEX IF NOT EXISTS idx_tickets_tenant ON tickets (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tickets_provider ON tickets (provider_id);
