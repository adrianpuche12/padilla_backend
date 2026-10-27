-- Campos del panel del operador que faltaban en reservas
ALTER TABLE padilla_dev.reservas
    ADD COLUMN IF NOT EXISTS tipo_inmueble      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS asesor             VARCHAR(150),
    ADD COLUMN IF NOT EXISTS fecha_recibo       DATE,
    ADD COLUMN IF NOT EXISTS medio_pago         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS observaciones      TEXT,
    ADD COLUMN IF NOT EXISTS apoderado_locativa VARCHAR(200);

-- Campos del garante que faltaban
ALTER TABLE padilla_dev.reserva_garantes
    ADD COLUMN IF NOT EXISTS apellido_materno  VARCHAR(150),
    ADD COLUMN IF NOT EXISTS fecha_nacimiento  DATE;
