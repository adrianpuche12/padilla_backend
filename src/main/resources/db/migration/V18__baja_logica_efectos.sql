-- =====================================================================
-- V18 (PRO-033, 2026-10-28): marcas para la baja logica (ARQ 7.2 regla 5).
-- Al dar de baja un propietario o un inquilino su contrato activo no se
-- toca: queda marcado "propietario inactivo" / "inquilino inactivo".
-- =====================================================================
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS owner_inactive boolean NOT NULL DEFAULT FALSE;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS tenant_inactive boolean NOT NULL DEFAULT FALSE;

-- Quien dio de baja a cada usuario y cuando (auditoria basica de la baja logica)
ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at timestamptz;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_by uuid;

CREATE INDEX IF NOT EXISTS idx_users_created_by ON users (created_by);
