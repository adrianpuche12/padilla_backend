package com.padilla.backend.service.notification;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.repository.PropertyRepository;
import com.padilla.backend.service.notification.NotificationService.Notice;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PRO-060: a quien se avisa en cada evento de un ticket (ARQ 4.1, tabla "eventos por actor").
 * Los textos hablan de "la inmobiliaria", nunca del empleado que hizo el cambio.
 */
@Component
@RequiredArgsConstructor
public class TicketNotifications {

    private static final String REF = "TICKET";

    private final NotificationService notifications;
    private final PropertyRepository propertyRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${app.db.schema}")
    private String schema;

    @EventListener
    public void onCreated(TicketEvents.Created e) {
        Ticket t = e.ticket();
        String n = numero(t);
        notifications.notify(notifications.management(), Notice.of(NotificationType.TICKET_CREADO,
                "Nuevo ticket " + n, "Se abrió el ticket " + n + ": " + t.getTitle() + ".", REF, t.getId()));
        owner(t).ifPresent(o -> notifications.notify(o, Notice.of(NotificationType.TICKET_CREADO,
                "Nuevo ticket en tu propiedad", "Se registró el ticket " + n + " (" + t.getTitle() + ") en una de tus propiedades. "
                        + "La inmobiliaria ya lo está gestionando.", REF, t.getId())));
        notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_CREADO,
                "Recibimos tu pedido " + n, "Tu pedido \"" + t.getTitle() + "\" fue registrado. Te vamos a avisar cada novedad.",
                REF, t.getId()));
    }

    @EventListener
    public void onStatusChanged(TicketEvents.StatusChanged e) {
        Ticket t = e.ticket();
        String n = numero(t);
        switch (e.to()) {
            case EN_PROCESO -> notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_EN_PROGRESO,
                    "Tu pedido " + n + " está en curso", "La inmobiliaria está trabajando en tu pedido \"" + t.getTitle() + "\".", REF, t.getId()));
            case RESUELTA -> {
                List<UUID> a = new ArrayList<>(notifications.management());
                a.add(t.getTenantId());
                owner(t).ifPresent(a::add);
                notifications.notify(a, Notice.of(NotificationType.TICKET_COMPLETADO, "Ticket " + n + " resuelto",
                        "El trabajo del ticket " + n + " (" + t.getTitle() + ") quedó finalizado.", REF, t.getId()));
            }
            case CERRADA -> notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_CERRADO,
                    "Ticket " + n + " cerrado", "Tu pedido \"" + t.getTitle() + "\" se cerró. ¡Gracias!", REF, t.getId()));
            case RECHAZADA -> notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_RECHAZADO,
                    "Tu pedido " + n + " no fue aceptado", "La inmobiliaria no pudo tomar tu pedido \"" + t.getTitle() + "\". Motivo: "
                            + Optional.ofNullable(t.getRejectionReason()).orElse("sin detalle") + ".", REF, t.getId()));
            case CANCELADA -> notifications.notify(notifications.management(), Notice.of(NotificationType.TICKET_CANCELADO,
                    "Ticket " + n + " cancelado", "Se canceló el ticket " + n + " (" + t.getTitle() + ").", REF, t.getId()).inAppOnly());
            default -> { }
        }
    }

    @EventListener
    public void onProviderAssigned(TicketEvents.ProviderAssigned e) {
        Ticket t = e.ticket();
        String n = numero(t);
        notifications.notify(t.getProviderId(), Notice.of(NotificationType.TICKET_ASIGNADO,
                "Nueva asignación " + n, "La inmobiliaria te asignó el ticket " + n + ": " + t.getTitle() + ".", REF, t.getId()));
        notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_ASIGNADO,
                "Tu pedido " + n + " tiene un profesional asignado",
                "La inmobiliaria asignó un profesional para resolver \"" + t.getTitle() + "\".", REF, t.getId()));
    }

    @EventListener
    public void onComment(TicketEvents.CommentAdded e) {
        if (e.internal()) {
            return;
        }
        Ticket t = e.ticket();
        // Avisa al inquilino y al proveedor, menos a quien escribio
        List<UUID> a = new ArrayList<>();
        a.add(t.getTenantId());
        a.add(t.getProviderId());
        a.removeIf(id -> id == null || id.equals(e.authorId()));
        notifications.notify(a, Notice.of(NotificationType.TICKET_COMENTARIO, "Novedad en el ticket " + numero(t),
                "Hay un mensaje nuevo en el ticket " + numero(t) + ".", REF, t.getId()).inAppOnly());
    }

    @EventListener
    public void onScheduled(TicketEvents.Scheduled e) {
        Ticket t = e.ticket();
        String cuando = t.getScheduledAt().atZoneSameInstant(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        notifications.notify(t.getTenantId(), Notice.of(NotificationType.TICKET_ASIGNADO, "Visita coordinada " + numero(t),
                "La inmobiliaria coordinó la visita para tu pedido \"" + t.getTitle() + "\" el " + cuando + ".", REF, t.getId()));
    }

    private Optional<UUID> owner(Ticket t) {
        return propertyRepository.findById(t.getPropertyId()).map(p -> p.getOwnerId());
    }

    /** Numero visible del ticket. Recien creado lo asigna la secuencia de la base: se lee de ahi. */
    private String numero(Ticket t) {
        Integer n = t.getTicketNumber();
        if (n == null) {
            List<Integer> r = jdbc.queryForList("SELECT ticket_number FROM " + schema + ".tickets WHERE id = ?", Integer.class, t.getId());
            n = r.isEmpty() ? null : r.get(0);
        }
        return n == null ? "" : "#" + n;
    }
}
