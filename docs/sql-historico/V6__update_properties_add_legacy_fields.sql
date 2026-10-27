-- V6__update_properties_add_legacy_fields.sql
-- Sprint 7: ampliar tabla properties con campos del sistema legacy (Propiedades.xls)
-- Reemplazar 'padilla_dev' por 'padilla_staging' o 'padilla' según el ambiente

-- Nuevos campos legacy
ALTER TABLE padilla_dev.properties
    ADD COLUMN IF NOT EXISTS legacy_id      INTEGER,
    ADD COLUMN IF NOT EXISTS street         VARCHAR(150),
    ADD COLUMN IF NOT EXISTS street_number  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS floor          VARCHAR(10),
    ADD COLUMN IF NOT EXISTS apartment      VARCHAR(10),
    ADD COLUMN IF NOT EXISTS city           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS province       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rental_status  VARCHAR(30),
    ADD COLUMN IF NOT EXISTS sale_status    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS entry_date     DATE,
    ADD COLUMN IF NOT EXISTS rental_producer VARCHAR(150),
    ADD COLUMN IF NOT EXISTS sale_producer   VARCHAR(150);

-- Migrar valores del enum PropertyType anterior al nuevo
-- (solo aplica si hay datos de prueba con los valores viejos)
UPDATE padilla_dev.properties SET type = 'CASA'        WHERE type = 'HOUSE';
UPDATE padilla_dev.properties SET type = 'DEPARTAMENTO' WHERE type = 'APARTMENT';
UPDATE padilla_dev.properties SET type = 'LOCAL'        WHERE type = 'COMMERCIAL';
UPDATE padilla_dev.properties SET type = 'TERRENO'      WHERE type = 'LAND';

-- Índices
CREATE INDEX IF NOT EXISTS idx_properties_legacy_id ON padilla_dev.properties(legacy_id);
CREATE INDEX IF NOT EXISTS idx_properties_city      ON padilla_dev.properties(city);
