package com.padilla.backend.service.dashboard;

import com.padilla.backend.enums.Role;
import com.padilla.backend.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static com.padilla.backend.enums.Role.ADMIN;
import static com.padilla.backend.enums.Role.MANAGER;
import static com.padilla.backend.enums.Role.OWNER;
import static com.padilla.backend.enums.Role.PROVIDER;
import static com.padilla.backend.enums.Role.SUPER_ADMIN;
import static com.padilla.backend.enums.Role.TENANT;

/**
 * PRO-070: catalogo inicial de widgets (Widget Registry §5 y §12) + widgets por rol (W7, ARQ §9).
 * Cada @Bean es un DashboardWidget; agregar uno nuevo es agregar un metodo aca (o un @Component).
 * Las consultas corren con la agencia fijada (RLS): no filtran por agencia a mano.
 */
@Configuration
public class CoreWidgets {

    private static final Set<Role> STAFF = Set.of(SUPER_ADMIN, MANAGER, ADMIN);
    private static final Set<Role> SUPERVISION = Set.of(SUPER_ADMIN, MANAGER);

    private final JdbcTemplate jdbc;
    private final String s;

    public CoreWidgets(JdbcTemplate jdbc, @Value("${app.db.schema}") String schema) {
        this.jdbc = jdbc;
        this.s = schema + ".";
    }

    record SimpleWidget(Metadata metadata, BiFunction<UUID, Map<String, String>, Object> data) implements DashboardWidget {
        @Override
        public Object fetchData(UUID agencyId, Map<String, String> params) {
            return data.apply(agencyId, params);
        }
    }

    private static DashboardWidget.Metadata meta(String code, String title, String desc, String category, int catOrder, int order,
                                                 Set<Role> roles, Set<Role> defaults) {
        return new DashboardWidget.Metadata(code, title, desc, category, catOrder, order, roles, defaults);
    }

    // --- OPERACIONES ---

    @Bean
    DashboardWidget ticketsOpen() {
        return new SimpleWidget(meta("TICKETS_OPEN", "Tickets abiertos", "Tickets abiertos y en proceso", "OPERACIONES", 1, 1, STAFF, STAFF),
                (a, p) -> jdbc.queryForMap("SELECT count(*) FILTER (WHERE status = 'ABIERTA') AS abiertos, "
                        + "count(*) FILTER (WHERE status = 'EN_PROCESO') AS \"enProceso\", "
                        + "count(*) FILTER (WHERE status IN ('ABIERTA','EN_PROCESO')) AS total FROM " + s + "tickets"));
    }

    @Bean
    DashboardWidget ticketsSla() {
        return new SimpleWidget(meta("TICKETS_SLA", "Fuera de SLA", "Tickets abiertos sin tomar hace mas del SLA", "OPERACIONES", 1, 2,
                SUPERVISION, SUPERVISION),
                (a, p) -> {
                    int horas = jdbc.queryForList("SELECT sla_horas FROM " + s + "agency_settings", Integer.class).stream().findFirst().orElse(48);
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("slaHoras", horas);
                    r.put("fueraDeSla", jdbc.queryForObject("SELECT count(*) FROM " + s + "tickets WHERE status = 'ABIERTA' "
                            + "AND created_at < now() - make_interval(hours => ?)", Long.class, horas));
                    return r;
                });
    }

    @Bean
    DashboardWidget ticketsByType() {
        return new SimpleWidget(meta("TICKETS_BY_TYPE", "Tickets por tipo", "Ultimos 90 dias, por categoria", "OPERACIONES", 1, 3, STAFF, STAFF),
                (a, p) -> jdbc.queryForList("SELECT category AS categoria, count(*) AS cantidad FROM " + s + "tickets "
                        + "WHERE created_at >= now() - interval '90 days' GROUP BY category ORDER BY cantidad DESC"));
    }

    @Bean
    DashboardWidget ticketsAvgTime() {
        return new SimpleWidget(meta("TICKETS_AVG_TIME", "Tiempo de resolucion", "Promedio en horas, ultimos 90 dias", "OPERACIONES", 1, 4,
                SUPERVISION, Set.of()),
                (a, p) -> jdbc.queryForMap("SELECT round(avg(extract(epoch FROM (closed_at - created_at)) / 3600)::numeric, 1) AS horas, "
                        + "count(*) AS tickets FROM " + s + "tickets WHERE closed_at IS NOT NULL AND status IN ('CERRADA','RESUELTA') "
                        + "AND closed_at >= now() - interval '90 days'"));
    }

    // --- PROPIEDADES ---

    @Bean
    DashboardWidget propertiesStatus() {
        return new SimpleWidget(meta("PROPERTIES_STATUS", "Estado de propiedades", "Propiedades activas por estado", "PROPIEDADES", 2, 1,
                STAFF, STAFF),
                (a, p) -> jdbc.queryForList("SELECT status AS estado, count(*) AS cantidad FROM " + s + "properties "
                        + "WHERE active GROUP BY status ORDER BY cantidad DESC"));
    }

    @Bean
    DashboardWidget propertiesIncidents() {
        return new SimpleWidget(meta("PROPERTIES_INCIDENTS", "Propiedades con mas incidencias", "Top 5 del ultimo ano", "PROPIEDADES", 2, 2,
                SUPERVISION, Set.of()),
                (a, p) -> jdbc.queryForList("SELECT pr.id, pr.address AS direccion, count(t.id) AS tickets FROM " + s + "tickets t JOIN "
                        + s + "properties pr ON pr.id = t.property_id WHERE t.created_at >= now() - interval '365 days' "
                        + "GROUP BY pr.id, pr.address ORDER BY tickets DESC LIMIT 5"));
    }

    @Bean
    DashboardWidget contractsExpiring() {
        return new SimpleWidget(meta("CONTRACTS_EXPIRING", "Contratos por vencer", "Vencen en los proximos 60 dias", "PROPIEDADES", 2, 3,
                STAFF, STAFF),
                (a, p) -> jdbc.queryForList("SELECT c.id, pr.address AS direccion, c.end_date AS vence FROM " + s + "contracts c JOIN "
                        + s + "properties pr ON pr.id = c.property_id WHERE c.status = 'ACTIVE' "
                        + "AND c.end_date <= current_date + 60 ORDER BY c.end_date LIMIT 10"));
    }

    @Bean
    DashboardWidget contractsActive() {
        return new SimpleWidget(meta("CONTRACTS_ACTIVE", "Contratos activos", "Contratos vigentes", "PROPIEDADES", 2, 4, STAFF, STAFF),
                (a, p) -> Map.of("activos", jdbc.queryForObject("SELECT count(*) FROM " + s + "contracts WHERE status = 'ACTIVE'", Long.class)));
    }

    // --- INQUILINOS ---

    @Bean
    DashboardWidget tenantsActive() {
        return new SimpleWidget(meta("TENANTS_ACTIVE", "Inquilinos activos", "Inquilinos con cuenta activa", "INQUILINOS", 3, 1, STAFF, Set.of()),
                (a, p) -> Map.of("activos", jdbc.queryForObject("SELECT count(*) FROM " + s + "users WHERE role = 'TENANT' AND active", Long.class)));
    }

    @Bean
    DashboardWidget tenantsSatisfaction() {
        return new SimpleWidget(meta("TENANTS_SATISFACTION", "Satisfaccion", "Promedio de encuestas, ultimos 90 dias", "INQUILINOS", 3, 2,
                SUPERVISION, SUPERVISION),
                (a, p) -> jdbc.queryForMap("SELECT round(avg(puntuacion)::numeric, 2) AS promedio, count(*) AS respuestas, "
                        + "count(*) FILTER (WHERE puntuacion <= 2) AS negativas FROM " + s + "surveys "
                        + "WHERE answered_at >= now() - interval '90 days'"));
    }

    // --- PROFESIONALES ---

    @Bean
    DashboardWidget providersRanking() {
        return new SimpleWidget(meta("PROVIDERS_RANKING", "Ranking de profesionales", "Top 5 por puntuacion", "PROFESIONALES", 4, 1,
                SUPERVISION, SUPERVISION),
                (a, p) -> jdbc.queryForList("SELECT u.id, u.name AS nombre, round(avg(sv.puntuacion)::numeric, 2) AS promedio, "
                        + "count(sv.id) AS evaluaciones FROM " + s + "surveys sv JOIN " + s + "users u ON u.id = sv.provider_id "
                        + "WHERE sv.puntuacion IS NOT NULL GROUP BY u.id, u.name ORDER BY promedio DESC, evaluaciones DESC LIMIT 5"));
    }

    @Bean
    DashboardWidget providersActive() {
        return new SimpleWidget(meta("PROVIDERS_ACTIVE", "Profesionales activos", "Proveedores habilitados", "PROFESIONALES", 4, 2, STAFF, Set.of()),
                (a, p) -> Map.of("activos", jdbc.queryForObject("SELECT count(*) FROM " + s + "users WHERE role = 'PROVIDER' AND active", Long.class)));
    }

    // --- MI CUENTA (dashboard por rol, ARQ §9) ---

    @Bean
    DashboardWidget myTickets() {
        return new SimpleWidget(meta("MY_TICKETS", "Mis tickets", "Tus tickets por estado", "MI_CUENTA", 0, 1,
                Set.of(OWNER, TENANT, PROVIDER), Set.of(OWNER, TENANT, PROVIDER)),
                (a, p) -> {
                    UUID me = CurrentUser.id();
                    String filtro = switch (CurrentUser.role()) {
                        case TENANT -> "tenant_id = ?";
                        case PROVIDER -> "provider_id = ?";
                        default -> "property_id IN (SELECT id FROM " + s + "properties WHERE owner_id = ?)";
                    };
                    return jdbc.queryForList("SELECT status AS estado, count(*) AS cantidad FROM " + s + "tickets WHERE " + filtro
                            + " GROUP BY status", me);
                });
    }

    @Bean
    DashboardWidget myProperties() {
        return new SimpleWidget(meta("MY_PROPERTIES", "Mis propiedades", "Tus propiedades y su estado", "MI_CUENTA", 0, 2,
                Set.of(OWNER), Set.of(OWNER)),
                (a, p) -> jdbc.queryForList("SELECT status AS estado, count(*) AS cantidad FROM " + s + "properties "
                        + "WHERE owner_id = ? AND active GROUP BY status", CurrentUser.id()));
    }

    @Bean
    DashboardWidget myContract() {
        return new SimpleWidget(meta("MY_CONTRACT", "Mi contrato", "Contrato vigente de tu vivienda", "MI_CUENTA", 0, 3,
                Set.of(TENANT), Set.of(TENANT)),
                (a, p) -> {
                    List<Map<String, Object>> r = jdbc.queryForList("SELECT c.id, pr.address AS direccion, c.end_date AS vence, "
                            + "c.monthly_amount AS monto, c.currency AS moneda FROM " + s + "contracts c JOIN " + s
                            + "properties pr ON pr.id = c.property_id WHERE c.tenant_id = ? AND c.status = 'ACTIVE' LIMIT 1", CurrentUser.id());
                    return r.isEmpty() ? Map.of() : r.get(0);
                });
    }

    @Bean
    DashboardWidget myRating() {
        return new SimpleWidget(meta("MY_RATING", "Mi puntuacion", "Promedio de las encuestas de tus trabajos", "MI_CUENTA", 0, 4,
                Set.of(PROVIDER), Set.of(PROVIDER)),
                (a, p) -> jdbc.queryForMap("SELECT round(avg(puntuacion)::numeric, 2) AS promedio, count(*) AS evaluaciones FROM "
                        + s + "surveys WHERE provider_id = ? AND puntuacion IS NOT NULL", CurrentUser.id()));
    }
}
