-- ============================================================
-- Módulo 3: Tickets / Incidencias
-- Sprint 3 — T03-002 / T03-003
-- Ejecutar manualmente en cada schema antes del deploy:
--   psql -U padilla -d padilla -c "\i V3__create_tickets.sql"
-- Reemplazar el schema según el ambiente:
--   padilla_dev / padilla_staging / padilla
-- ============================================================

CREATE TABLE IF NOT EXISTS padilla_dev.tickets (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    description      TEXT         NOT NULL,
    category         VARCHAR(50)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ABIERTA',
    priority         VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    property_id      UUID         NOT NULL REFERENCES padilla_dev.properties(id),
    tenant_id        UUID         NOT NULL REFERENCES padilla_dev.users(id),
    provider_id      UUID         REFERENCES padilla_dev.users(id),
    rejection_reason TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tickets_tenant_id
    ON padilla_dev.tickets(tenant_id);

CREATE INDEX IF NOT EXISTS idx_tickets_status
    ON padilla_dev.tickets(status);

CREATE INDEX IF NOT EXISTS idx_tickets_property_id
    ON padilla_dev.tickets(property_id);

CREATE INDEX IF NOT EXISTS idx_tickets_provider_id
    ON padilla_dev.tickets(provider_id);

CREATE TABLE IF NOT EXISTS padilla_dev.ticket_comments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID        NOT NULL REFERENCES padilla_dev.tickets(id) ON DELETE CASCADE,
    author_id   UUID        NOT NULL REFERENCES padilla_dev.users(id),
    content     TEXT        NOT NULL,
    internal    BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ticket_comments_ticket_id
    ON padilla_dev.ticket_comments(ticket_id);
