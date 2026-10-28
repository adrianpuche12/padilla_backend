-- =====================================================================
-- V16 (PRO-020 / PRO-021, 2026-10-27): multi-inmobiliaria con RLS.
--
-- Cada inmobiliaria (agencia) es un tenant del SaaS. Toda tabla de negocio
-- lleva agency_id y una politica RLS que solo deja ver/escribir las filas
-- de la agencia fijada en la transaccion:
--
--     SET LOCAL app.current_agency = '<uuid>'   (nunca SET a secas: con pool
--                                                 de conexiones se filtraria)
--
-- La app lo hace en AgencyAwareJpaTransactionManager al iniciar cada
-- transaccion. Sin agencia fijada no se ve ninguna fila (fail-closed).
--
-- Se usa agency_id y no tenant_id porque tenant_id ya significa "inquilino".
--
-- IMPORTANTE: RLS no aplica a superusuarios. La app tiene que conectarse con
-- el rol propero_app (creado aca, sin LOGIN: la password la pone operaciones,
-- ver docs/MULTI-INMOBILIARIA.md). Flyway sigue corriendo con el dueno.
--
-- Migraciones futuras que modifiquen datos de estas tablas deben empezar con
--     SET LOCAL app.rls_bypass = 'on';
-- =====================================================================

-- Agencias -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS agencies (
    id          uuid PRIMARY KEY,
    name        varchar(150) NOT NULL,
    slug        varchar(60)  NOT NULL UNIQUE,
    active      boolean      NOT NULL DEFAULT TRUE,
    plan        varchar(20)  NOT NULL DEFAULT 'STARTER',
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- Padilla: primera agencia, duena de todos los datos existentes
INSERT INTO agencies (id, name, slug, plan)
VALUES ('00000000-0000-0000-0000-000000000001', 'Inmobiliaria Padilla', 'padilla', 'PRO')
ON CONFLICT (id) DO NOTHING;

-- Funciones de contexto -----------------------------------------------
CREATE OR REPLACE FUNCTION propero_current_agency() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('app.current_agency', true), '')::uuid $$;

CREATE OR REPLACE FUNCTION propero_rls_bypass() RETURNS boolean
    LANGUAGE sql STABLE AS
$$ SELECT coalesce(current_setting('app.rls_bypass', true), '') = 'on' $$;

-- agency_id + RLS en cada tabla de negocio -----------------------------
DO $$
DECLARE
    t text;
    tablas text[] := ARRAY['users', 'client_profiles', 'properties', 'contracts', 'contract_periods',
                           'tickets', 'ticket_comments', 'ticket_provider_log', 'reservas', 'reserva_garantes'];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS agency_id uuid', t);
        EXECUTE format('UPDATE %I SET agency_id = %L WHERE agency_id IS NULL', t, '00000000-0000-0000-0000-000000000001');
        EXECUTE format('ALTER TABLE %I ALTER COLUMN agency_id SET NOT NULL', t);
        -- Las inserciones toman la agencia de la transaccion: las entidades no necesitan conocerla
        EXECUTE format('ALTER TABLE %I ALTER COLUMN agency_id SET DEFAULT propero_current_agency()', t);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (agency_id)', 'idx_' || t || '_agency', t);
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_' || t || '_agency') THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (agency_id) REFERENCES agencies(id)',
                           t, 'fk_' || t || '_agency');
        END IF;
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS agency_isolation ON %I', t);
        EXECUTE format('CREATE POLICY agency_isolation ON %I '
                       'USING (agency_id = propero_current_agency() OR propero_rls_bypass()) '
                       'WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass())', t);
    END LOOP;
END $$;

-- Rol de la aplicacion (sin superusuario: RLS le aplica) ----------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'propero_app') THEN
        CREATE ROLE propero_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END $$;

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO propero_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ${flyway:defaultSchema} TO propero_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ${flyway:defaultSchema} TO propero_app;
GRANT EXECUTE ON FUNCTION propero_current_agency(), propero_rls_bypass() TO propero_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema} GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO propero_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema} GRANT USAGE, SELECT ON SEQUENCES TO propero_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${flyway:defaultSchema} GRANT EXECUTE ON FUNCTIONS TO propero_app;
