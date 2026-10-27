-- ============================================================
-- Módulo 2: Contratos — actualización de tabla + períodos de indexación
-- Sprint 7 — T02-008
-- Ejecutar en cada schema antes del deploy:
--   psql -U padilla -d padilla-db -c "\i V5__update_contracts_add_periods.sql"
-- Reemplazar el schema según el ambiente:
--   padilla_dev / padilla_staging / padilla
-- ============================================================

-- Nuevos campos en la tabla contracts existente
ALTER TABLE padilla_dev.contracts
    ADD COLUMN IF NOT EXISTS legacy_id       INTEGER,
    ADD COLUMN IF NOT EXISTS signing_date    DATE,
    ADD COLUMN IF NOT EXISTS contract_type   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS commission_pct  NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS admin_fee_pct   NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS co_owner        VARCHAR(150),
    ADD COLUMN IF NOT EXISTS co_tenant       VARCHAR(150),
    ADD COLUMN IF NOT EXISTS notes           TEXT,
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(150);

-- Índice para búsqueda por número legacy
CREATE INDEX IF NOT EXISTS idx_contracts_legacy_id
    ON padilla_dev.contracts(legacy_id);

-- Nueva tabla: períodos de indexación de cada contrato
CREATE TABLE IF NOT EXISTS padilla_dev.contract_periods (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id         UUID           NOT NULL REFERENCES padilla_dev.contracts(id) ON DELETE CASCADE,
    period_from         DATE           NOT NULL,
    rent_amount         NUMERIC(12,2)  NOT NULL,
    commission_amount   NUMERIC(12,2),
    admin_fee_amount    NUMERIC(12,2),
    adjustment_index    NUMERIC(10,4),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Índices para consultas frecuentes
CREATE INDEX IF NOT EXISTS idx_contract_periods_contract_id
    ON padilla_dev.contract_periods(contract_id);

CREATE INDEX IF NOT EXISTS idx_contract_periods_period_from
    ON padilla_dev.contract_periods(period_from);
