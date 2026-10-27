-- Locador / propietario en reservas (feedback cliente 2026-06-08).
-- No se ingresa en el formulario del inquilino; lo carga el operador y fluye al contrato de locación (CTR).
ALTER TABLE padilla_dev.reservas
    ADD COLUMN IF NOT EXISTS locador_nombre    VARCHAR(150),
    ADD COLUMN IF NOT EXISTS locador_apellido  VARCHAR(150),
    ADD COLUMN IF NOT EXISTS locador_dni       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS locador_domicilio VARCHAR(255);
