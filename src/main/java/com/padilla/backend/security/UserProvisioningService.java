package com.padilla.backend.security;

import com.padilla.backend.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRO-005: provision "just in time". Si un usuario valido de Keycloak llama al API y no tiene fila
 * en users (por ejemplo el usuario demo creado a mano en Keycloak), se crea con los datos del token.
 * Sin esto, toda escritura con FK a users (reservas.operador_id, ticket_provider_log.changed_by,
 * ticket_comments.author_id) daba 500 y la pantalla Usuarios quedaba vacia.
 *
 * PRO-021: el INSERT corre en una transaccion con la agencia de AgencyContext, asi agency_id toma
 * su valor del DEFAULT propero_current_agency(). Las verificaciones previas (si ya existe, si el
 * email esta tomado) miran todas las agencias, porque id y email son unicos en todo el sistema.
 */
@Slf4j
@Service
public class UserProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate tx;
    private final RlsBypass rlsBypass;
    private final String schema;
    /** Ids ya verificados en esta instancia: evita un SELECT por request. */
    private final Set<UUID> known = ConcurrentHashMap.newKeySet();

    public UserProvisioningService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                                   RlsBypass rlsBypass, @Value("${app.db.schema}") String schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.tx = new TransactionTemplate(transactionManager);
        this.rlsBypass = rlsBypass;
        this.schema = schema;
    }

    public void ensureProvisioned() {
        UUID id = CurrentUser.id();
        if (known.contains(id)) {
            return;
        }
        if (countAllAgencies("SELECT count(*) FROM " + schema + ".users WHERE id = ?", id) > 0) {
            known.add(id);
            return;
        }

        Role role = CurrentUser.role();
        String email = CurrentUser.claim("email")
                .or(() -> CurrentUser.claim("preferred_username"))
                .map(String::toLowerCase)
                .orElse(id + "@sin-email.propero");
        String name = CurrentUser.claim("name")
                .or(UserProvisioningService::fullName)
                .or(() -> CurrentUser.claim("preferred_username"))
                .orElse(email);

        if (countAllAgencies("SELECT count(*) FROM " + schema + ".users WHERE lower(email) = ?", email) > 0) {
            // El email ya pertenece a otra fila con distinto id: no se pisa. Se registra para revisar a mano.
            log.warn("Provision JIT omitida: el email {} ya existe en users con otro id (sub del token {})", email, id);
            known.add(id);
            return;
        }

        Integer inserted = tx.execute(s -> jdbcTemplate.update(
                "INSERT INTO " + schema + ".users (id, name, email, role, active, created_at, first_login) "
                        + "VALUES (?, ?, ?, ?, TRUE, NOW(), FALSE) ON CONFLICT (id) DO NOTHING",
                id, name, email, role.name()));
        if (inserted != null && inserted > 0) {
            log.info("Usuario {} ({}) creado en users desde el token de Keycloak con rol {} en la agencia {}",
                    email, id, role, AgencyContext.current().orElse(null));
        }
        known.add(id);
    }

    private int countAllAgencies(String sql, Object arg) {
        Integer n = rlsBypass.call(() -> jdbcTemplate.queryForObject(sql, Integer.class, arg));
        return n == null ? 0 : n;
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
