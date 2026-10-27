package com.padilla.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Datos de prueba directos en la base de test (schema propero_test) y JWT simulados por usuario. */
@Component
public class TestData {

    public static final String SCHEMA = "propero_test";

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".users (id, name, email, role, active, created_at, first_login) "
                + "VALUES (?, ?, ?, ?, TRUE, NOW(), FALSE)", id, role + " " + id.toString().substring(0, 6),
                id + "@propero.test", role);
        return id;
    }

    public void deactivate(UUID userId) {
        jdbc.update("UPDATE " + SCHEMA + ".users SET active = FALSE WHERE id = ?", userId);
    }

    public UUID property(UUID ownerId, UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".properties (id, active, address, created_at, owner_id, status, type, tenant_id) "
                + "VALUES (?, TRUE, ?, NOW(), ?, ?, 'DEPARTAMENTO', ?)", id, "Calle " + id.toString().substring(0, 4),
                ownerId, tenantId == null ? "AVAILABLE" : "RENTED", tenantId);
        return id;
    }

    public UUID activeContract(UUID propertyId, UUID ownerId, UUID tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".contracts (id, active, created_at, currency, end_date, monthly_amount, owner_id, "
                + "property_id, start_date, status, tenant_id) VALUES (?, TRUE, NOW(), 'ARS', ?, 100000, ?, ?, ?, 'ACTIVE', ?)",
                id, LocalDate.now().plusYears(2), ownerId, propertyId, LocalDate.now().minusMonths(1), tenantId);
        return id;
    }

    public UUID ticket(UUID propertyId, UUID tenantId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".tickets (id, category, created_at, description, priority, property_id, status, "
                + "tenant_id, title) VALUES (?, 'PLOMERIA', NOW(), 'Pierde la canilla', 'NORMAL', ?, ?, ?, 'Canilla')",
                id, propertyId, status, tenantId);
        return id;
    }

    /** JWT simulado del usuario (sub = id) con su rol. */
    public static JwtRequestPostProcessor as(UUID userId, String role) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@propero.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
