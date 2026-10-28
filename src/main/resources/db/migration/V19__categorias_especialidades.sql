-- =====================================================================
-- V19 (PRO-041, 2026-10-28): nuevas categorias de ticket (ALBANILERIA,
-- JARDINERIA) para que cada especialidad de proveedor tenga su categoria.
-- =====================================================================
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_category_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_category_check CHECK (category IN
    ('PLOMERIA', 'ELECTRICIDAD', 'PINTURA', 'CERRAJERIA', 'GAS', 'ALBANILERIA', 'JARDINERIA', 'OTRO'));
