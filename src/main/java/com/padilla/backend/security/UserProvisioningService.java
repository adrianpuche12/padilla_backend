package com.padilla.backend.security;

import com.padilla.backend.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRO-005: provision "just in time". Si un usuario valido de Keycloak llama al API y no tiene fila
 * en users (por ejemplo el usuario demo creado a mano en Keycloak), se crea con los datos del token.
 * Sin esto, toda escritura con FK a users (reservas.operador_id, ticket_provider_log.changed_by,
 * ticket_comments.author_id) daba 500 y la pantalla Usuarios quedaba vacia.
 */
@Slf4j
@Service
public class UserProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final String schema;
    /** Ids ya verificados en esta instancia: evita un SELECT por request. */
    private final Set<UUID> known = ConcurrentHashMap.newKeySet();

    public UserProvisioningService(JdbcTemplate jdbcTemplate, @Value("${app.db.schema}") String schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.schema = schema;
    }

    public void ensureProvisioned() {
        UUID id = CurrentUser.id();
        if (known.contains(id)) {
            return;
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + schema + ".users WHERE id = ?", Integer.class, id);
        if (existing != null && existing > 0) {
            known.add(id);
            return;
        }
        provision(id);
    }

    private void provision(UUID id) {
        Role role = CurrentUser.role();
        String email = CurrentUser.claim("email")
                .or(() -> CurrentUser.claim("preferred_username"))
                .map(String::toLowerCase)
                .orElse(id + "@sin-email.propero");
        String name = CurrentUser.claim("name")
                .or(() -> fullName())
                .or(() -> CurrentUser.claim("preferred_username"))
                .orElse(email);

        Integer emailTaken = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + schema + ".users WHERE lower(email) = ?", Integer.class, email);
        if (emailTaken != null && emailTaken > 0) {
            // El email ya pertenece a otra fila con distinto id: no se pisa. Se registra para revisar a mano.
            log.warn("Provision JIT omitida: el email {} ya existe en users con otro id (sub del token {})", email, id);
            known.add(id);
            return;
        }

        int inserted = jdbcTemplate.update(
                "INSERT INTO " + schema + ".users (id, name, email, role, active, created_at, first_login) "
                        + "VALUES (?, ?, ?, ?, TRUE, NOW(), FALSE) ON CONFLICT (id) DO NOTHING",
                id, name, email, role.name());
        if (inserted > 0) {
            log.info("Usuario {} ({}) creado en users desde el token de Keycloak con rol {}", email, id, role);
        }
        known.add(id);
    }

    private static Optional<String> fullName() {
        String given = CurrentUser.claim("given_name").orElse("");
        String family = CurrentUser.claim("family_name").orElse("");
        String full = (given + " " + family).trim();
        return full.isEmpty() ? Optional.empty() : Optional.of(full);
    }

    /** Para tests y para cuando se borra un usuario: vuelve a verificarlo en el proximo request. */
    public void forget(UUID id) {
        known.remove(id);
    }
}
