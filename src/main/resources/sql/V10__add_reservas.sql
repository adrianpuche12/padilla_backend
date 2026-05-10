CREATE TABLE IF NOT EXISTS padilla_dev.reservas (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre              VARCHAR(150) NOT NULL,
    apellido            VARCHAR(150) NOT NULL,
    apellido_materno    VARCHAR(150),
    dni                 VARCHAR(20)  NOT NULL,
    cuil                VARCHAR(20),
    fecha_nacimiento    DATE,
    domicilio_actual    VARCHAR(255),
    telefono            VARCHAR(50),
    email               VARCHAR(150),
    condicion_fiscal    VARCHAR(80),
    profesion           VARCHAR(100),
    propiedad           VARCHAR(255) NOT NULL,
    property_id         UUID         REFERENCES padilla_dev.properties(id),
    monto               NUMERIC(14,2) NOT NULL,
    monto_informes      NUMERIC(14,2),
    tipo_garantia       VARCHAR(100),
    operador_id         UUID         REFERENCES padilla_dev.users(id),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    cantidad_personas   INTEGER,
    tiene_co_inquilino  BOOLEAN      DEFAULT false,
    co_inquilino_nombre VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reservas_dni         ON padilla_dev.reservas(dni);
CREATE INDEX IF NOT EXISTS idx_reservas_operador_id ON padilla_dev.reservas(operador_id);
CREATE INDEX IF NOT EXISTS idx_reservas_created_at  ON padilla_dev.reservas(created_at DESC);

CREATE TABLE IF NOT EXISTS padilla_dev.reserva_garantes (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id       UUID        NOT NULL REFERENCES padilla_dev.reservas(id) ON DELETE CASCADE,
    nombre           VARCHAR(200) NOT NULL,
    dni              VARCHAR(20),
    cuil             VARCHAR(20),
    domicilio        VARCHAR(255),
    descripcion_bien TEXT
);

CREATE INDEX IF NOT EXISTS idx_reserva_garantes_reserva_id ON padilla_dev.reserva_garantes(reserva_id);
