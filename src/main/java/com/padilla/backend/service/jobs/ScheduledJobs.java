package com.padilla.backend.service.jobs;

import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.service.notification.NotificationPreferences;
import com.padilla.backend.service.notification.NotificationService;
import com.padilla.backend.service.notification.NotificationService.Notice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tareas programadas (decision D6: automatizaciones core en el backend).
 * - PRO-044: contratos vencidos -> EXPIRED (libera la propiedad) y alerta 30 dias antes del vencimiento.
 * - PRO-054: tickets sin atender mas de sla_horas (configurable por agencia) -> alerta a supervisores.
 * - PRO-063: resumen diario / semanal para quien lo pidio.
 * Cada metodo publico tambien se puede llamar a mano (tests, operaciones).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final AgencyJobRunner runner;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final NotificationPreferences preferences;
    private final Clock clock;

    @Value("${app.db.schema}")
    private String schema;

    @Scheduled(cron = "${app.jobs.daily-cron:0 0 6 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void daily() {
        LocalDate hoy = LocalDate.now(clock);
        contractsExpiration(hoy);
        slaAlerts();
        digests(hoy);
    }

    // --- PRO-044 ---

    public void contractsExpiration(LocalDate hoy) {
        runner.forEachAgency("vencimientos", agency -> {
            List<Map<String, Object>> vencidos = jdbc.queryForList("UPDATE " + schema + ".contracts SET status = 'EXPIRED' "
                    + "WHERE status = 'ACTIVE' AND end_date < ? RETURNING id, property_id, owner_id", hoy);
            for (Map<String, Object> c : vencidos) {
                UUID contrato = (UUID) c.get("id");
                jdbc.update("UPDATE " + schema + ".properties SET tenant_id = NULL, status = 'AVAILABLE' WHERE id = ?", c.get("property_id"));
                List<UUID> a = new ArrayList<>(notifications.management());
                a.add((UUID) c.get("owner_id"));
                notifications.notify(a, Notice.of(NotificationType.CONTRATO_VENCIDO, "Contrato vencido",
                        "Venció el contrato de " + direccion((UUID) c.get("property_id")) + ". La propiedad quedó disponible.",
                        "CONTRATO", contrato).inAppOnly());
            }
            List<Map<String, Object>> porVencer = jdbc.queryForList("SELECT id, property_id, owner_id, end_date FROM " + schema
                    + ".contracts WHERE status = 'ACTIVE' AND end_date BETWEEN ? AND ?", hoy, hoy.plusDays(30));
            for (Map<String, Object> c : porVencer) {
                List<UUID> a = new ArrayList<>(notifications.management());
                a.add((UUID) c.get("owner_id"));
                notifications.notify(a, Notice.of(NotificationType.CONTRATO_POR_VENCER, "Contrato por vencer",
                        "El contrato de " + direccion((UUID) c.get("property_id")) + " vence el " + c.get("end_date") + ".",
                        "CONTRATO", (UUID) c.get("id")));
            }
            if (!vencidos.isEmpty() || !porVencer.isEmpty()) {
                log.info("Agencia {}: {} contratos vencidos, {} por vencer", agency, vencidos.size(), porVencer.size());
            }
        });
    }

    // --- PRO-054: SLA ---

    public void slaAlerts() {
        runner.forEachAgency("sla", agency -> {
            Integer horas = jdbc.queryForList("SELECT sla_horas FROM " + schema + ".agency_settings", Integer.class)
                    .stream().findFirst().orElse(48);
            List<Map<String, Object>> atrasados = jdbc.queryForList("SELECT id, ticket_number, title FROM " + schema
                    + ".tickets WHERE status = 'ABIERTA' AND created_at < now() - make_interval(hours => ?)", horas);
            for (Map<String, Object> t : atrasados) {
                notifications.notify(notifications.supervisors(), Notice.of(NotificationType.SLA_INCUMPLIDO,
                        "Ticket #" + t.get("ticket_number") + " sin atender",
                        "El ticket #" + t.get("ticket_number") + " (" + t.get("title") + ") lleva más de " + horas
                                + " horas abierto sin tomarse.", "TICKET", (UUID) t.get("id")).urgent());
            }
        });
    }

    // --- PRO-063: resumenes ---

    public void digests(LocalDate hoy) {
        runner.forEachAgency("resumenes", agency -> {
            send("DIARIO", 1, NotificationType.RESUMEN_DIARIO);
            if (hoy.getDayOfWeek() == DayOfWeek.MONDAY) {
                send("SEMANAL", 7, NotificationType.RESUMEN_SEMANAL);
            }
        });
    }

    private void send(String frecuencia, int dias, NotificationType tipo) {
        List<UUID> destinatarios = preferences.withDigest(frecuencia);
        if (destinatarios.isEmpty()) return;
        Map<String, Object> r = jdbc.queryForMap("SELECT "
                + "(SELECT count(*) FROM " + schema + ".tickets WHERE created_at >= now() - make_interval(days => ?)) AS nuevos, "
                + "(SELECT count(*) FROM " + schema + ".tickets WHERE closed_at >= now() - make_interval(days => ?)) AS cerrados, "
                + "(SELECT count(*) FROM " + schema + ".tickets WHERE status IN ('ABIERTA','EN_PROCESO')) AS abiertos, "
                + "(SELECT count(*) FROM " + schema + ".contracts WHERE status = 'ACTIVE' AND end_date <= current_date + 30) AS por_vencer",
                dias, dias);
        String periodo = dias == 1 ? "últimas 24 horas" : "última semana";
        String mensaje = "Resumen de la " + periodo + ": " + r.get("nuevos") + " tickets nuevos, " + r.get("cerrados")
                + " cerrados, " + r.get("abiertos") + " abiertos en total y " + r.get("por_vencer") + " contratos que vencen en los próximos 30 días.";
        notifications.notify(destinatarios, Notice.of(tipo, "Resumen " + frecuencia.toLowerCase(), mensaje, null, null));
    }

    private String direccion(UUID propertyId) {
        return jdbc.queryForList("SELECT address FROM " + schema + ".properties WHERE id = ?", String.class, propertyId)
                .stream().findFirst().orElse("la propiedad");
    }
}
