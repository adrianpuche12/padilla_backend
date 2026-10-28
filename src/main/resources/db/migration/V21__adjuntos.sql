-- =====================================================================
-- V21 (PRO-046 / PRO-050, 2026-10-28): archivos adjuntos.
-- Fotos de tickets, documentos de contratos y propiedades, escrituras y
-- poderes de propietarios, certificados de proveedores (con vencimiento).
-- El archivo vive en el StorageService (disco/volumen; listo para S3/MinIO,
-- decision D10); aca solo los metadatos.
-- =====================================================================
CREATE TABLE IF NOT EXISTS attachments (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id     uuid NOT NULL DEFAULT propero_current_agency() REFERENCES agencies(id),
    entity_type   varchar(20)  NOT NULL,
    entity_id     uuid         NOT NULL,
    nombre        varchar(255) NOT NULL,
    content_type  varchar(100) NOT NULL,
    size_bytes    bigint       NOT NULL,
    storage_key   varchar(500) NOT NULL UNIQUE,
    descripcion   varchar(255),
    vence         date,
    uploaded_by   uuid REFERENCES users(id),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT attachments_entity_check CHECK (entity_type IN
        ('TICKET', 'CONTRATO', 'PROPIEDAD', 'CLIENTE', 'PROVEEDOR', 'RESERVA'))
);
CREATE INDEX IF NOT EXISTS idx_attachments_entity ON attachments (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_attachments_agency ON attachments (agency_id);

ALTER TABLE attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attachments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS agency_isolation ON attachments;
CREATE POLICY agency_isolation ON attachments
    USING (agency_id = propero_current_agency() OR propero_rls_bypass())
    WITH CHECK (agency_id = propero_current_agency() OR propero_rls_bypass());
GRANT SELECT, INSERT, UPDATE, DELETE ON attachments TO propero_app;
