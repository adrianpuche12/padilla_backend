-- =====================================================================
-- V17 (PRO-024 / PRO-025, 2026-10-28): configuracion por inmobiliaria.
--
-- Datos legales y de marca de cada agencia. Los usan los documentos de
-- reserva (FC1, FC10, CTR) y los mails, que hasta ahora tenian escritos
-- en el codigo el nombre, la firma, la matricula y el mail de Padilla.
-- Tambien las reglas operativas (SLA, monto critico) y los limites del plan.
-- =====================================================================
SET LOCAL app.rls_bypass = 'on';

CREATE TABLE IF NOT EXISTS agency_settings (
    agency_id            uuid PRIMARY KEY REFERENCES agencies(id) DEFAULT propero_current_agency(),
    -- Identidad legal (documentos)
    razon_social         varchar(200),
    nombre_comercial     varchar(200),
    corredor_nombre      varchar(150),
    corredor_matricula   varchar(100),
    colegio              varchar(150),
    cuit                 varchar(20),
    email_contratos      varchar(150),
    telefono             varchar(50),
    domicilio            varchar(200),
    ciudad               varchar(100),
    provincia            varchar(100),
    jurisdiccion         varchar(200),
    comision_pct         numeric(5,2),
    comision_iva         boolean NOT NULL DEFAULT TRUE,
    plataforma_pago      varchar(100),
    -- Marca
    logo_url             varchar(500),
    color_primario       varchar(7),
    -- Reglas operativas (PRO-084 / PRO-053 / PRO-054)
    sla_horas            integer NOT NULL DEFAULT 48,
    monto_critico        numeric(14,2),
    email_desde_hora     smallint NOT NULL DEFAULT 8,
    email_hasta_hora     smallint NOT NULL DEFAULT 20,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid
);

ALTER TABLE agency_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE agency_settings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON agency_settings;
CREATE POLICY agency_isolation ON agency_settings
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());

-- Padilla: los datos que estaban escritos en pages/Reservas.jsx del frontend
INSERT INTO agency_settings (agency_id, razon_social, nombre_comercial, corredor_nombre, corredor_matricula,
                             colegio, email_contratos, ciudad, provincia, jurisdiccion, comision_pct,
                             plataforma_pago)
VALUES ('00000000-0000-0000-0000-000000000001',
        'SEBASTIAN RAMASCO PADILLA, NEGOCIOS INMOBILIARIOS',
        'S. RAMASCO PADILLA - Negocios Inmobiliarios',
        'Sebastián Ramasco Padilla',
        'Mat. C.I.C.I.C.P.S.F. N° 829',
        'Colegio de Corredores Inmobiliarios de la Provincia de Santa Fe',
        'contratos@sramascopadilla.com.ar',
        'Rosario', 'Santa Fe',
        'Tribunales Ordinarios de la ciudad de Rosario',
        5.00, 'GEXION')
ON CONFLICT (agency_id) DO NOTHING;

-- Limites del plan (PRO-025): viven en el enum Plan del backend (STARTER / PRO / SCALE).

GRANT SELECT, INSERT, UPDATE, DELETE ON agency_settings TO propero_app;
GRANT SELECT ON agencies TO propero_app;
