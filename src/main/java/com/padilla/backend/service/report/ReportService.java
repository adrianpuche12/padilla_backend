package com.padilla.backend.service.report;

import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.report.Report.Column;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-072: reportes (ARQ 4 Mod.6) con filtros universales: fecha, actor, propiedad, tipo de servicio,
 * costo y puntuacion. Corren con la agencia fijada (RLS).
 * - tickets, costos, proveedores: reportes operativos (SUPER_ADMIN, MANAGER, ADMIN).
 * - eficiencia: reporte global (SUPER_ADMIN, MANAGER).
 * - propietario-anual: informe anual del propietario (la inmobiliaria o el propio propietario).
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final JdbcTemplate jdbc;

    @Value("${app.db.schema}")
    private String schema;

    public record Filters(LocalDate from, LocalDate to, UUID propertyId, UUID ownerId, UUID providerId, String category,
                          BigDecimal minCost, BigDecimal maxCost, Integer minRating, Integer year) {
        LocalDate desde() { return from != null ? from : LocalDate.now().minusDays(90); }
        LocalDate hasta() { return to != null ? to : LocalDate.now(); }
        String periodo() {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return "Período: " + desde().format(f) + " al " + hasta().format(f);
        }
    }

    @Transactional(readOnly = true)
    public Report build(String tipo, Filters f) {
        return switch (tipo) {
            case "tickets" -> { requireOperational(); yield tickets(f); }
            case "costos" -> { requireOperational(); yield costos(f); }
            case "proveedores" -> { requireOperational(); yield proveedores(f); }
            case "eficiencia" -> { requireGlobal(); yield eficiencia(f); }
            case "propietario-anual" -> propietarioAnual(f);
            default -> throw new BadRequestException("Reporte inexistente: " + tipo
                    + ". Disponibles: tickets, costos, proveedores, eficiencia, propietario-anual");
        };
    }

    private Report tickets(Filters f) {
        List<Object> args = new ArrayList<>(List.of(f.desde(), f.hasta().plusDays(1)));
        StringBuilder where = new StringBuilder(" WHERE t.created_at >= ? AND t.created_at < ?");
        if (f.propertyId() != null) { where.append(" AND t.property_id = ?"); args.add(f.propertyId()); }
        if (f.ownerId() != null) { where.append(" AND p.owner_id = ?"); args.add(f.ownerId()); }
        if (f.providerId() != null) { where.append(" AND t.provider_id = ?"); args.add(f.providerId()); }
        if (f.category() != null) { where.append(" AND t.category = ?"); args.add(f.category().toUpperCase()); }
        if (f.minCost() != null) { where.append(" AND t.costo_final >= ?"); args.add(f.minCost()); }
        if (f.maxCost() != null) { where.append(" AND t.costo_final <= ?"); args.add(f.maxCost()); }
        if (f.minRating() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM " + schema + ".surveys s WHERE s.ticket_id = t.id AND s.puntuacion >= ?)");
            args.add(f.minRating());
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.ticket_number AS numero, to_char(t.created_at, 'DD/MM/YYYY') AS fecha, "
                + "t.title AS titulo, t.category AS categoria, t.status AS estado, p.address AS propiedad, pv.name AS proveedor, "
                + "t.costo_final AS costo, round((extract(epoch FROM (t.closed_at - t.created_at)) / 3600)::numeric, 1) AS horas "
                + "FROM " + schema + ".tickets t JOIN " + schema + ".properties p ON p.id = t.property_id "
                + "LEFT JOIN " + schema + ".users pv ON pv.id = t.provider_id" + where + " ORDER BY t.created_at DESC LIMIT 5000",
                args.toArray());
        return new Report("Tickets", f.periodo(), List.of(new Column("numero", "N°"), new Column("fecha", "Fecha"),
                new Column("titulo", "Título"), new Column("categoria", "Tipo"), new Column("estado", "Estado"),
                new Column("propiedad", "Propiedad"), new Column("proveedor", "Proveedor"), new Column("costo", "Costo"),
                new Column("horas", "Horas hasta cierre")), rows);
    }

    private Report costos(Filters f) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.address AS propiedad, o.name AS propietario, count(t.id) AS tickets, "
                + "coalesce(sum(t.costo_final), 0) AS total, round(coalesce(avg(t.costo_final), 0), 2) AS promedio "
                + "FROM " + schema + ".tickets t JOIN " + schema + ".properties p ON p.id = t.property_id "
                + "LEFT JOIN " + schema + ".users o ON o.id = p.owner_id WHERE t.created_at >= ? AND t.created_at < ? "
                + "AND (?::uuid IS NULL OR p.owner_id = ?::uuid) "
                + "GROUP BY p.address, o.name ORDER BY total DESC LIMIT 1000", f.desde(), f.hasta().plusDays(1), f.ownerId(), f.ownerId());
        return new Report("Costos de mantenimiento por propiedad", f.periodo(), List.of(new Column("propiedad", "Propiedad"),
                new Column("propietario", "Propietario"), new Column("tickets", "Tickets"), new Column("total", "Costo total"),
                new Column("promedio", "Costo promedio")), rows);
    }

    private Report proveedores(Filters f) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.name AS proveedor, cp.specialty AS especialidad, "
                + "count(t.id) FILTER (WHERE t.status IN ('RESUELTA','CERRADA')) AS resueltos, "
                + "(SELECT round(avg(s.puntuacion)::numeric, 2) FROM " + schema + ".surveys s WHERE s.provider_id = u.id AND s.puntuacion IS NOT NULL) AS rating, "
                + "round(avg(t.costo_final), 2) AS costo_promedio, "
                + "round(avg(extract(epoch FROM (t.closed_at - t.created_at)) / 3600)::numeric, 1) AS horas_promedio "
                + "FROM " + schema + ".users u LEFT JOIN " + schema + ".client_profiles cp ON cp.user_id = u.id "
                + "LEFT JOIN " + schema + ".tickets t ON t.provider_id = u.id AND t.created_at >= ? AND t.created_at < ? "
                + "WHERE u.role = 'PROVIDER' AND u.active GROUP BY u.id, u.name, cp.specialty "
                + "ORDER BY rating DESC NULLS LAST, resueltos DESC", f.desde(), f.hasta().plusDays(1));
        if (f.minRating() != null) {
            rows = rows.stream().filter(r -> r.get("rating") != null
                    && ((BigDecimal) r.get("rating")).compareTo(BigDecimal.valueOf(f.minRating())) >= 0).toList();
        }
        return new Report("Ranking de profesionales", f.periodo(), List.of(new Column("proveedor", "Proveedor"),
                new Column("especialidad", "Especialidad"), new Column("resueltos", "Resueltos"), new Column("rating", "Puntuación"),
                new Column("costo_promedio", "Costo promedio"), new Column("horas_promedio", "Horas promedio")), rows);
    }

    private Report eficiencia(Filters f) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.name AS empleado, u.role AS rol, count(l.id) AS asignaciones, "
                + "count(DISTINCT l.ticket_id) AS tickets, round(avg(extract(epoch FROM (l.changed_at - t.created_at)) / 3600)::numeric, 1) "
                + "AS horas_hasta_asignar FROM " + schema + ".users u JOIN " + schema + ".ticket_provider_log l ON l.changed_by = u.id "
                + "JOIN " + schema + ".tickets t ON t.id = l.ticket_id WHERE u.role IN ('ADMIN','MANAGER','SUPER_ADMIN') "
                + "AND l.changed_at >= ? AND l.changed_at < ? GROUP BY u.id, u.name, u.role ORDER BY asignaciones DESC",
                f.desde(), f.hasta().plusDays(1));
        return new Report("Eficiencia del equipo", f.periodo(), List.of(new Column("empleado", "Empleado"), new Column("rol", "Rol"),
                new Column("asignaciones", "Asignaciones"), new Column("tickets", "Tickets"),
                new Column("horas_hasta_asignar", "Horas promedio hasta asignar")), rows);
    }

    private Report propietarioAnual(Filters f) {
        Role role = CurrentUser.role();
        UUID owner = role == Role.OWNER ? CurrentUser.id() : f.ownerId();
        if (role == Role.OWNER && f.ownerId() != null && !f.ownerId().equals(CurrentUser.id())) {
            throw new RbacException("Solo podes ver tu propio informe");
        }
        if (!role.isManagement() && role != Role.OWNER) {
            throw new RbacException("El rol " + role + " no tiene informes de propietario");
        }
        if (owner == null) throw new BadRequestException("Indica el propietario (ownerId)");
        int year = f.year() != null ? f.year() : LocalDate.now().getYear();
        LocalDate desde = LocalDate.of(year, 1, 1);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.address AS propiedad, "
                + "(SELECT count(*) FROM " + schema + ".tickets t WHERE t.property_id = p.id AND t.created_at >= ? AND t.created_at < ?) AS tickets, "
                + "(SELECT coalesce(sum(t.costo_final), 0) FROM " + schema + ".tickets t WHERE t.property_id = p.id AND t.created_at >= ? AND t.created_at < ?) AS gastos, "
                + "(SELECT c.monthly_amount FROM " + schema + ".contracts c WHERE c.property_id = p.id AND c.status = 'ACTIVE' LIMIT 1) AS alquiler, "
                + "(SELECT to_char(c.end_date, 'DD/MM/YYYY') FROM " + schema + ".contracts c WHERE c.property_id = p.id AND c.status = 'ACTIVE' LIMIT 1) AS vence "
                + "FROM " + schema + ".properties p WHERE p.owner_id = ? ORDER BY p.address",
                desde, desde.plusYears(1), desde, desde.plusYears(1), owner);
        String nombre = jdbc.queryForList("SELECT name FROM " + schema + ".users WHERE id = ?", String.class, owner).stream().findFirst().orElse("");
        return new Report("Informe anual " + year + " — " + nombre, "Propiedades, incidencias y gastos del año",
                List.of(new Column("propiedad", "Propiedad"), new Column("tickets", "Incidencias"), new Column("gastos", "Gastos"),
                        new Column("alquiler", "Alquiler vigente"), new Column("vence", "Vence")), rows);
    }

    private static void requireOperational() {
        if (!CurrentUser.isManagement()) throw new RbacException("Reporte disponible solo para la inmobiliaria");
    }

    private static void requireGlobal() {
        Role r = CurrentUser.role();
        if (r != Role.SUPER_ADMIN && r != Role.MANAGER) throw new RbacException("Reporte global: solo SUPER_ADMIN y MANAGER");
    }
}
