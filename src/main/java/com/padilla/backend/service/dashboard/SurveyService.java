package com.padilla.backend.service.dashboard;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.enums.TicketStatus;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.repository.PropertyRepository;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.NotificationService;
import com.padilla.backend.service.notification.NotificationService.Notice;
import com.padilla.backend.service.notification.TicketEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-071: encuesta de satisfaccion (ARQ 4 Mod.5).
 * Al cerrar un ticket se crea una encuesta pendiente para el inquilino y el propietario.
 * Una evaluacion de 1 o 2 estrellas avisa a los supervisores (FEEDBACK_NEGATIVO).
 * El promedio de las encuestas del inquilino es el rating del proveedor que hizo el trabajo.
 */
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final JdbcTemplate jdbc;
    private final PropertyRepository propertyRepository;
    private final NotificationService notifications;

    @Value("${app.db.schema}")
    private String schema;

    @EventListener
    public void onTicketChanged(TicketEvents.StatusChanged e) {
        if (e.to() != TicketStatus.CERRADA) return;
        Ticket t = e.ticket();
        crear(t, t.getTenantId(), "TENANT");
        propertyRepository.findById(t.getPropertyId()).map(p -> p.getOwnerId()).ifPresent(o -> crear(t, o, "OWNER"));
    }

    private void crear(Ticket t, UUID userId, String rol) {
        int n = jdbc.update("INSERT INTO " + schema + ".surveys (ticket_id, user_id, rol, provider_id) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (ticket_id, user_id) DO NOTHING", t.getId(), userId, rol, t.getProviderId());
        if (n > 0) {
            notifications.notify(userId, Notice.of(NotificationType.FEEDBACK_RECIBIDO, "¿Cómo resolvimos tu pedido?",
                    "Contanos en un minuto cómo fue la atención del ticket #" + t.getTicketNumber() + ".", "TICKET", t.getId()).inAppOnly());
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending() {
        return jdbc.queryForList("SELECT s.id, s.ticket_id AS \"ticketId\", t.ticket_number AS \"ticketNumber\", t.title, s.created_at AS \"createdAt\" "
                + "FROM " + schema + ".surveys s JOIN " + schema + ".tickets t ON t.id = s.ticket_id "
                + "WHERE s.user_id = ? AND s.answered_at IS NULL ORDER BY s.created_at DESC", CurrentUser.id());
    }

    @Transactional
    public Map<String, Object> answer(UUID surveyId, int puntuacion, String comentario) {
        if (puntuacion < 1 || puntuacion > 5) {
            throw new BadRequestException("La puntuacion va de 1 a 5");
        }
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM " + schema + ".surveys WHERE id = ? AND user_id = ?",
                surveyId, CurrentUser.id());
        if (r.isEmpty()) throw new NotFoundException("Encuesta no encontrada: " + surveyId);
        if (r.get(0).get("answered_at") != null) throw new ConflictException("La encuesta ya fue respondida");
        jdbc.update("UPDATE " + schema + ".surveys SET puntuacion = ?, comentario = ?, answered_at = now() WHERE id = ?",
                puntuacion, comentario, surveyId);
        UUID ticket = (UUID) r.get(0).get("ticket_id");
        if (puntuacion <= 2) {
            notifications.notify(notifications.supervisors(), Notice.of(NotificationType.FEEDBACK_NEGATIVO, "Evaluación negativa",
                    "Un ticket recibió " + puntuacion + " estrella" + (puntuacion == 1 ? "" : "s")
                            + (comentario == null || comentario.isBlank() ? "." : ": \"" + comentario + "\"."), "TICKET", ticket).urgent());
        }
        return jdbc.queryForMap("SELECT id, puntuacion, comentario, answered_at AS \"answeredAt\" FROM " + schema + ".surveys WHERE id = ?", surveyId);
    }

    /** Rating del proveedor: promedio de las encuestas del inquilino de los tickets que resolvio. */
    @Transactional(readOnly = true)
    public Map<String, Object> providerRating(UUID providerId) {
        return jdbc.queryForMap("SELECT round(avg(puntuacion)::numeric, 2) AS promedio, count(*) AS evaluaciones FROM " + schema
                + ".surveys WHERE provider_id = ? AND rol = 'TENANT' AND puntuacion IS NOT NULL", providerId);
    }
}
