-- =====================================================================
-- V13 (PRO-004 / PRO-011, 2026-10-27)
-- En Propero dev la tabla ticket_provider_log se creo SIN la columna
-- changed_by que define el script historico V9. Por eso asignar un
-- proveedor daba 500 (bug reportado por Peter el 27-Oct).
-- Idempotente: no hace nada donde la columna ya existe.
-- =====================================================================
ALTER TABLE ticket_provider_log ADD COLUMN IF NOT EXISTS changed_by uuid;

DO $$
BEGIN
    -- NOT NULL solo si no hay filas sin dato (no se inventan autores para el historial existente)
    IF NOT EXISTS (SELECT 1 FROM ticket_provider_log WHERE changed_by IS NULL) THEN
        ALTER TABLE ticket_provider_log ALTER COLUMN changed_by SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tpl_changed_by ON ticket_provider_log (changed_by);
