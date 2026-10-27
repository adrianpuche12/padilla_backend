-- =====================================================================
-- V1__baseline.sql  (PRO-003, 2026-10-27)
-- Estado del schema de Propero equivalente a los scripts manuales V3..V12
-- (ver docs/sql-historico). Generado desde pg_dump -s de padilla_dev
-- (Propero dev), sin prefijo de schema: Flyway lo aplica en app.db.schema.
--
-- Ambientes que YA tienen estas tablas (padilla_dev) no ejecutan este
-- archivo: spring.flyway.baseline-on-migrate + baseline-version=12.
-- Las tablas de leads (seller, source, *_dashboard, daily_leads) NO van
-- aca: pertenecen a la base legacy (LegacyDataSourceConfig).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- client_profiles; Type: TABLE
CREATE TABLE client_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    legacy_id integer,
    persona_type character varying(10) DEFAULT 'FISICA'::character varying NOT NULL,
    document_type character varying(20),
    document_number character varying(50),
    cuit_cuil character varying(20),
    address character varying(255),
    city character varying(100),
    province character varying(100),
    iva_condition character varying(50),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone,
    specialty character varying(30)
);

-- contract_periods; Type: TABLE
CREATE TABLE contract_periods (
    id uuid NOT NULL,
    adjustment_index numeric(10,4),
    admin_fee_amount numeric(12,2),
    commission_amount numeric(12,2),
    contract_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    period_from date NOT NULL,
    rent_amount numeric(12,2) NOT NULL
);

-- contracts; Type: TABLE
CREATE TABLE contracts (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(3) NOT NULL,
    end_date date NOT NULL,
    monthly_amount numeric(12,2) NOT NULL,
    owner_id uuid NOT NULL,
    property_id uuid NOT NULL,
    start_date date NOT NULL,
    status character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    admin_fee_pct numeric(5,2),
    co_owner character varying(150),
    co_tenant character varying(150),
    commission_pct numeric(5,2),
    contract_type character varying(255),
    legacy_id integer,
    notes text,
    producer character varying(150),
    signing_date date,
    CONSTRAINT contracts_contract_type_check CHECK (((contract_type)::text = ANY ((ARRAY['PARTICULAR'::character varying, 'COMERCIAL'::character varying, 'COMERCIAL_ICL_SEMESTRAL'::character varying, 'ICL_TRIMESTRAL'::character varying, 'ICL_CUATRIMESTRAL'::character varying, 'ICL_SEMESTRAL_VIVIENDA'::character varying, 'SIN_ASIGNAR'::character varying])::text[]))),
    CONSTRAINT contracts_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'TERMINATED'::character varying])::text[])))
);

-- properties; Type: TABLE
CREATE TABLE properties (
    id uuid NOT NULL,
    active boolean NOT NULL,
    address character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    owner_id uuid NOT NULL,
    rooms integer,
    square_meters double precision,
    status character varying(255) NOT NULL,
    tenant_id uuid,
    type character varying(255) NOT NULL,
    apartment character varying(10),
    city character varying(100),
    entry_date date,
    floor character varying(10),
    legacy_id integer,
    province character varying(100),
    rental_producer character varying(150),
    rental_status character varying(30),
    sale_producer character varying(150),
    sale_status character varying(30),
    street character varying(150),
    street_number character varying(20),
    CONSTRAINT properties_rental_status_check CHECK (((rental_status)::text = ANY ((ARRAY['ALQUILADA'::character varying, 'ANULADA'::character varying, 'CONTRATO_EN_CONFECCION'::character varying, 'EN_ALQUILER'::character varying, 'NO_CORRESPONDE'::character varying, 'SUSPENDIDA'::character varying])::text[]))),
    CONSTRAINT properties_sale_status_check CHECK (((sale_status)::text = ANY ((ARRAY['ANULADA'::character varying, 'EN_VENTA'::character varying, 'NO_CORRESPONDE'::character varying])::text[]))),
    CONSTRAINT properties_status_check CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'RENTED'::character varying, 'FOR_SALE'::character varying, 'MAINTENANCE'::character varying])::text[])))
);

-- reserva_garantes; Type: TABLE
CREATE TABLE reserva_garantes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    reserva_id uuid NOT NULL,
    nombre character varying(200) NOT NULL,
    dni character varying(20),
    cuil character varying(20),
    domicilio character varying(255),
    descripcion_bien text,
    apellido_materno character varying(150),
    fecha_nacimiento date
);

-- reservas; Type: TABLE
CREATE TABLE reservas (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(150) NOT NULL,
    apellido character varying(150) NOT NULL,
    apellido_materno character varying(150),
    dni character varying(20) NOT NULL,
    cuil character varying(20),
    fecha_nacimiento date,
    domicilio_actual character varying(255),
    telefono character varying(50),
    email character varying(150),
    condicion_fiscal character varying(80),
    profesion character varying(100),
    propiedad character varying(255) NOT NULL,
    property_id uuid,
    monto numeric(14,2) NOT NULL,
    monto_informes numeric(14,2),
    tipo_garantia character varying(100),
    operador_id uuid,
    status character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    cantidad_personas integer,
    tiene_co_inquilino boolean DEFAULT false,
    co_inquilino_nombre character varying(200),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    tipo_inmueble character varying(50),
    asesor character varying(150),
    fecha_recibo date,
    medio_pago character varying(50),
    observaciones text,
    apoderado_locativa character varying(200),
    locador_nombre character varying(150),
    locador_apellido character varying(150),
    locador_dni character varying(20),
    locador_domicilio character varying(255)
);

-- ticket_comments; Type: TABLE
CREATE TABLE ticket_comments (
    id uuid NOT NULL,
    author_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    internal boolean NOT NULL,
    ticket_id uuid NOT NULL
);

-- ticket_provider_log; Type: TABLE
CREATE TABLE ticket_provider_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_id uuid NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    old_provider_id uuid,
    new_provider_id uuid
);

-- tickets_ticket_number_seq; Type: SEQUENCE
CREATE SEQUENCE tickets_ticket_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- tickets; Type: TABLE
CREATE TABLE tickets (
    id uuid NOT NULL,
    category character varying(255) NOT NULL,
    closed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    description text NOT NULL,
    priority character varying(255) NOT NULL,
    property_id uuid NOT NULL,
    provider_id uuid,
    rejection_reason text,
    status character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone,
    ticket_number integer DEFAULT nextval('tickets_ticket_number_seq'::regclass) NOT NULL,
    CONSTRAINT tickets_category_check CHECK (((category)::text = ANY ((ARRAY['PLOMERIA'::character varying, 'ELECTRICIDAD'::character varying, 'PINTURA'::character varying, 'CERRAJERIA'::character varying, 'GAS'::character varying, 'OTRO'::character varying])::text[]))),
    CONSTRAINT tickets_priority_check CHECK (((priority)::text = ANY ((ARRAY['BAJA'::character varying, 'NORMAL'::character varying, 'URGENTE'::character varying])::text[]))),
    CONSTRAINT tickets_status_check CHECK (((status)::text = ANY ((ARRAY['ABIERTA'::character varying, 'EN_PROCESO'::character varying, 'RESUELTA'::character varying, 'CERRADA'::character varying, 'RECHAZADA'::character varying])::text[])))
);

-- users; Type: TABLE
CREATE TABLE users (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(255),
    role character varying(255) NOT NULL,
    password_reset_expires_at timestamp(6) with time zone,
    first_login boolean DEFAULT true NOT NULL,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['SUPER_ADMIN'::character varying, 'MANAGER'::character varying, 'ADMIN'::character varying, 'OWNER'::character varying, 'TENANT'::character varying, 'PROVIDER'::character varying])::text[])))
);

-- client_profiles client_profiles_pkey; Type: CONSTRAINT
ALTER TABLE ONLY client_profiles
    ADD CONSTRAINT client_profiles_pkey PRIMARY KEY (id);

-- client_profiles client_profiles_user_id_key; Type: CONSTRAINT
ALTER TABLE ONLY client_profiles
    ADD CONSTRAINT client_profiles_user_id_key UNIQUE (user_id);

-- contract_periods contract_periods_pkey; Type: CONSTRAINT
ALTER TABLE ONLY contract_periods
    ADD CONSTRAINT contract_periods_pkey PRIMARY KEY (id);

-- contracts contracts_pkey; Type: CONSTRAINT
ALTER TABLE ONLY contracts
    ADD CONSTRAINT contracts_pkey PRIMARY KEY (id);

-- properties properties_pkey; Type: CONSTRAINT
ALTER TABLE ONLY properties
    ADD CONSTRAINT properties_pkey PRIMARY KEY (id);

-- reserva_garantes reserva_garantes_pkey; Type: CONSTRAINT
ALTER TABLE ONLY reserva_garantes
    ADD CONSTRAINT reserva_garantes_pkey PRIMARY KEY (id);

-- reservas reservas_pkey; Type: CONSTRAINT
ALTER TABLE ONLY reservas
    ADD CONSTRAINT reservas_pkey PRIMARY KEY (id);

-- ticket_comments ticket_comments_pkey; Type: CONSTRAINT
ALTER TABLE ONLY ticket_comments
    ADD CONSTRAINT ticket_comments_pkey PRIMARY KEY (id);

-- ticket_provider_log ticket_provider_log_pkey; Type: CONSTRAINT
ALTER TABLE ONLY ticket_provider_log
    ADD CONSTRAINT ticket_provider_log_pkey PRIMARY KEY (id);

-- tickets tickets_pkey; Type: CONSTRAINT
ALTER TABLE ONLY tickets
    ADD CONSTRAINT tickets_pkey PRIMARY KEY (id);

-- tickets tickets_ticket_number_unique; Type: CONSTRAINT
ALTER TABLE ONLY tickets
    ADD CONSTRAINT tickets_ticket_number_unique UNIQUE (ticket_number);

-- users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT
ALTER TABLE ONLY users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);

-- users users_pkey; Type: CONSTRAINT
ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

-- idx_client_profiles_user_id; Type: INDEX
CREATE INDEX idx_client_profiles_user_id ON client_profiles USING btree (user_id);

-- idx_reserva_garantes_reserva_id; Type: INDEX
CREATE INDEX idx_reserva_garantes_reserva_id ON reserva_garantes USING btree (reserva_id);

-- idx_reservas_created_at; Type: INDEX
CREATE INDEX idx_reservas_created_at ON reservas USING btree (created_at DESC);

-- idx_reservas_dni; Type: INDEX
CREATE INDEX idx_reservas_dni ON reservas USING btree (dni);

-- idx_reservas_operador_id; Type: INDEX
CREATE INDEX idx_reservas_operador_id ON reservas USING btree (operador_id);

-- idx_tpl_changed; Type: INDEX
CREATE INDEX idx_tpl_changed ON ticket_provider_log USING btree (changed_at DESC);

-- idx_tpl_ticket; Type: INDEX
CREATE INDEX idx_tpl_ticket ON ticket_provider_log USING btree (ticket_id);

-- client_profiles client_profiles_user_id_fkey; Type: FK CONSTRAINT
ALTER TABLE ONLY client_profiles
    ADD CONSTRAINT client_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ticket_provider_log fk_ticket_log; Type: FK CONSTRAINT
ALTER TABLE ONLY ticket_provider_log
    ADD CONSTRAINT fk_ticket_log FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE;

-- reserva_garantes reserva_garantes_reserva_id_fkey; Type: FK CONSTRAINT
ALTER TABLE ONLY reserva_garantes
    ADD CONSTRAINT reserva_garantes_reserva_id_fkey FOREIGN KEY (reserva_id) REFERENCES reservas(id) ON DELETE CASCADE;

-- reservas reservas_operador_id_fkey; Type: FK CONSTRAINT
ALTER TABLE ONLY reservas
    ADD CONSTRAINT reservas_operador_id_fkey FOREIGN KEY (operador_id) REFERENCES users(id);

-- reservas reservas_property_id_fkey; Type: FK CONSTRAINT
ALTER TABLE ONLY reservas
    ADD CONSTRAINT reservas_property_id_fkey FOREIGN KEY (property_id) REFERENCES properties(id);


--
-- PostgreSQL database dump complete
--

