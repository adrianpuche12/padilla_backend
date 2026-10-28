-- =====================================================================
-- V26 (PRO-080, 2026-10-28): leads por inmobiliaria.
-- La base legacy de leads (n8n: portales, formularios, vendedores) es de
-- Padilla. Cada agencia decide si tiene ese modulo; las que no lo tienen
-- reciben listados vacios en vez de ver los leads de otra inmobiliaria.
-- =====================================================================
SET LOCAL app.rls_bypass = 'on';
ALTER TABLE agency_settings ADD COLUMN IF NOT EXISTS leads_enabled boolean NOT NULL DEFAULT FALSE;
UPDATE agency_settings SET leads_enabled = TRUE WHERE agency_id = '00000000-0000-0000-0000-000000000001';
