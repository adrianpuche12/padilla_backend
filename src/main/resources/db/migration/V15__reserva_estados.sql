-- =====================================================================
-- V15 (PRO-014, 2026-10-27): estados validos de una reserva (decision D8).
-- Antes el estado era texto libre. Valores fuera del catalogo pasan a PENDIENTE.
-- =====================================================================
UPDATE reservas SET status = 'PENDIENTE'
 WHERE status IS NULL OR status NOT IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA');

ALTER TABLE reservas DROP CONSTRAINT IF EXISTS reservas_status_check;
ALTER TABLE reservas ADD CONSTRAINT reservas_status_check
    CHECK (status IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'));
