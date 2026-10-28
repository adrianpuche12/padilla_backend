-- =====================================================================
-- V20 (PRO-060 / PRO-061, 2026-10-28): notificaciones (ARQ 4.1).
-- notification_log = log permanente + bandeja in-app de cada usuario.
-- (La spec propone Redis para las activas; decision D11: en el MVP se
-- lee de Postgres por polling. Redis/WebSocket quedan en el backlog.)
-- =====================================================================
CREATE TABLE IF NOT EXISTS notification_log (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id        uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    user_id          uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tipo             varchar(50)  NOT NULL,
    titulo           varchar(200) NOT NULL,
    mensaje          text         NOT NULL,
    referencia_tipo  varchar(20),
    referencia_id    uuid,
    prioridad        varchar(10)  NOT NULL DEFAULT 'NORMAL',
    canal            varchar(10)  NOT NULL DEFAULT 'IN_APP',
    email_enviado    boolean      NOT NULL DEFAULT FALSE,
    leida            boolean      NOT NULL DEFAULT FALSE,
    leida_at         timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT notification_prioridad_check CHECK (prioridad IN ('NORMAL', 'URGENTE')),
    CONSTRAINT notification_referencia_check CHECK (referencia_tipo IS NULL OR referencia_tipo IN
        ('TICKET', 'CONTRATO', 'PROPIEDAD', 'RESERVA', 'PRESUPUESTO', 'COMUNICADO'))
);

CREATE INDEX IF NOT EXISTS idx_notification_user_unread ON notification_log (user_id, leida, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_agency ON notification_log (agency_id);
-- Evita alertas duplicadas de un mismo evento (por ejemplo "contrato por vencer" del job diario)
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_evento
    ON notification_log (user_id, tipo, referencia_id) WHERE tipo IN ('CONTRATO_POR_VENCER', 'SLA_INCUMPLIDO');

ALTER TABLE notification_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON notification_log;
CREATE POLICY agency_isolation ON notification_log
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_log TO propero_app;

-- PRO-062: preferencias de notificacion por usuario (ARQ 4.1).
-- In-app siempre activo; el mail se puede apagar salvo los tipos obligatorios.
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id        uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    agency_id      uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    email_enabled  boolean     NOT NULL DEFAULT TRUE,
    resumen        varchar(10) NOT NULL DEFAULT 'OFF',
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notification_resumen_check CHECK (resumen IN ('OFF', 'DIARIO', 'SEMANAL'))
);
ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preferences FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON notification_preferences;
CREATE POLICY agency_isolation ON notification_preferences
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_preferences TO propero_app;
