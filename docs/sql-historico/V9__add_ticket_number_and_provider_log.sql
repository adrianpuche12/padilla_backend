-- ticket_number: correlativo visible por los usuarios
ALTER TABLE padilla_dev.tickets
    ADD COLUMN IF NOT EXISTS ticket_number SERIAL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tickets_ticket_number
    ON padilla_dev.tickets(ticket_number);

-- historial de cambios de proveedor en tickets
CREATE TABLE IF NOT EXISTS padilla_dev.ticket_provider_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID        NOT NULL REFERENCES padilla_dev.tickets(id) ON DELETE CASCADE,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by      UUID        NOT NULL REFERENCES padilla_dev.users(id),
    old_provider_id UUID        REFERENCES padilla_dev.users(id),
    new_provider_id UUID        REFERENCES padilla_dev.users(id)
);

CREATE INDEX IF NOT EXISTS idx_ticket_provider_log_ticket_id
    ON padilla_dev.ticket_provider_log(ticket_id);

CREATE INDEX IF NOT EXISTS idx_ticket_provider_log_changed_at
    ON padilla_dev.ticket_provider_log(changed_at DESC);
