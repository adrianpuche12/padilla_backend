-- =====================================================================
-- V24 (PRO-070 / PRO-071, 2026-10-28): Widget Registry y encuestas.
--
-- dashboard_widgets: catalogo GLOBAL de widgets (igual para todas las
-- agencias). Lo sincroniza el backend al arrancar desde los @Component que
-- implementan DashboardWidget (Dashboard Configurable - Widget Registry).
-- user_dashboard_preferences: que widgets ve cada usuario y en que orden.
-- surveys: encuesta de satisfaccion al cerrar un ticket (1-5 + comentario).
-- =====================================================================
CREATE TABLE IF NOT EXISTS dashboard_widgets (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code              varchar(60)  NOT NULL UNIQUE,
    title             varchar(120) NOT NULL,
    description       varchar(300),
    category          varchar(40)  NOT NULL,
    category_order    integer      NOT NULL DEFAULT 0,
    widget_order      integer      NOT NULL DEFAULT 0,
    allowed_roles     text[]       NOT NULL,
    is_system_active  boolean      NOT NULL DEFAULT TRUE,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_dashboard_preferences (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id   uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    widget_id   uuid NOT NULL REFERENCES dashboard_widgets(id) ON DELETE CASCADE,
    is_enabled  boolean NOT NULL DEFAULT FALSE,
    position    integer NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_widget UNIQUE (user_id, widget_id)
);
ALTER TABLE user_dashboard_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_dashboard_preferences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON user_dashboard_preferences;
CREATE POLICY agency_isolation ON user_dashboard_preferences
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());

CREATE TABLE IF NOT EXISTS surveys (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id     uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    ticket_id     uuid NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    user_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rol           varchar(20) NOT NULL,
    provider_id   uuid REFERENCES users(id),
    puntuacion    smallint CHECK (puntuacion BETWEEN 1 AND 5),
    comentario    text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    answered_at   timestamptz,
    CONSTRAINT uq_survey_ticket_user UNIQUE (ticket_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_surveys_provider ON surveys (provider_id) WHERE puntuacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_surveys_user_pending ON surveys (user_id) WHERE answered_at IS NULL;
ALTER TABLE surveys ENABLE ROW LEVEL SECURITY;
ALTER TABLE surveys FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON surveys;
CREATE POLICY agency_isolation ON surveys
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());

GRANT SELECT, INSERT, UPDATE, DELETE ON dashboard_widgets, user_dashboard_preferences, surveys TO propero_app;
