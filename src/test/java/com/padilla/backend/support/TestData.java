package com.padilla.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Datos de prueba directos en la base de test (schema propero_test) y JWT simulados por usuario.
 * PRO-021: usa el superusuario del contenedor (sin RLS) e inserta agency_id explicito.
 * Por defecto todo va a la agencia Padilla; usar los metodos *In(agency, ...) para otras.
 */
@Component
public class TestData {

    public static final String SCHEMA = "propero_test";
    public static final UUID PADILLA = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;

    public TestData() {
        this.jdbc = new JdbcTemplate(new DriverManagerDataSource(AbstractIntegrationTest.POSTGRES.getJdbcUrl(),
                AbstractIntegrationTest.POSTGRES.getUsername(), AbstractIntegrationTest.POSTGRES.getPassword()));
    }

    /** JdbcTemplate de superusuario: ve todas las agencias (para verificaciones en los tests). */
    public JdbcTemplate db() {
        return jdbc;
    }

    public UUID agency(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".agencies (id, name, slug) VALUES (?, ?, ?)", id, name,
                "a-" + id.toString().substring(0, 8));
        return id;
    }

    public UUID user(String role) {
        return userIn(PADILLA, role);
    }

    public UUID userIn(UUID agency, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".users (id, name, email, role, active, created_at, first_login, agency_id) "
                + "VALUES (?, ?, ?, ?, TRUE, NOW(), FALSE, ?)", id, role + " " + id.toString().substring(0, 6),
                id + "@propero.test", role, agency);
        return id;
    }

    public void deactivate(UUID userId) {
        jdbc.update("UPDATE " + SCHEMA + ".users SET active = FALSE WHERE id = ?", userId);
    }

    public UUID property(UUID ownerId, UUID tenantId) {
        return propertyIn(PADILLA, ownerId, tenantId);
    }

    public UUID propertyIn(UUID agency, UUID ownerId, UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".properties (id, active, address, created_at, owner_id, status, type, tenant_id, agency_id) "
                + "VALUES (?, TRUE, ?, NOW(), ?, ?, 'DEPARTAMENTO', ?, ?)", id, "Calle " + id.toString().substring(0, 4),
                ownerId, tenantId == null ? "AVAILABLE" : "RENTED", tenantId, agency);
        return id;
    }

    public UUID activeContract(UUID propertyId, UUID ownerId, UUID tenantId) {
        return activeContractIn(PADILLA, propertyId, ownerId, tenantId);
    }

    public UUID activeContractIn(UUID agency, UUID propertyId, UUID ownerId, UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".contracts (id, active, created_at, currency, end_date, monthly_amount, owner_id, "
                + "property_id, start_date, status, tenant_id, agency_id) VALUES (?, TRUE, NOW(), 'ARS', ?, 100000, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, LocalDate.now().plusYears(2), ownerId, propertyId, LocalDate.now().minusMonths(1), tenantId, agency);
        return id;
    }

    public UUID ticket(UUID propertyId, UUID tenantId, String status) {
        return ticketIn(PADILLA, propertyId, tenantId, status);
    }

    public UUID ticketIn(UUID agency, UUID propertyId, UUID tenantId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".tickets (id, category, created_at, description, priority, property_id, status, "
                + "tenant_id, title, agency_id) VALUES (?, 'PLOMERIA', NOW(), 'Pierde la canilla', 'NORMAL', ?, ?, ?, 'Canilla', ?)",
                id, propertyId, status, tenantId, agency);
        return id;
    }

    /** JWT simulado del usuario (sub = id) con su rol. */
    public static JwtRequestPostProcessor as(UUID userId, String role) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@propero.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
