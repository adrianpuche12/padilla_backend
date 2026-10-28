package com.padilla.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRO-021 (decision D1): la agencia de un usuario se resuelve en el backend desde users.agency_id
 * (un solo realm de Keycloak, sin mappers por agencia). La consulta corre sin filtro RLS porque
 * todavia no se sabe la agencia. Usuarios que solo existen en Keycloak van a app.default-agency-id
 * si esta configurada (dev), o quedan sin agencia (403).
 */
@Service
public class AgencyResolver {

    private final JdbcTemplate jdbc;
    private final RlsBypass rlsBypass;
    private final String schema;
    private final Optional<UUID> defaultAgency;
    private final Map<UUID, UUID> cache = new ConcurrentHashMap<>();

    public AgencyResolver(JdbcTemplate jdbc, RlsBypass rlsBypass,
                          @Value("${app.db.schema}") String schema,
                          @Value("${app.default-agency-id:}") String defaultAgencyId) {
        this.jdbc = jdbc;
        this.rlsBypass = rlsBypass;
        this.schema = schema;
        this.defaultAgency = defaultAgencyId.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(defaultAgencyId));
    }

    public Optional<UUID> agencyOf(UUID userId) {
        UUID cached = cache.get(userId);
        if (cached != null) {
            return Optional.of(cached);
        }
        List<UUID> found = rlsBypass.call(() -> jdbc.queryForList(
                "SELECT agency_id FROM " + schema + ".users WHERE id = ?", UUID.class, userId));
        if (found != null && !found.isEmpty()) {
            cache.put(userId, found.get(0));
            return Optional.of(found.get(0));
        }
        return Optional.empty();
    }

    /** Agencia para un usuario nuevo (provision JIT). */
    public Optional<UUID> defaultAgency() {
        return defaultAgency;
    }

    public void forget(UUID userId) {
        cache.remove(userId);
    }
}
