package com.padilla.backend.service.audit;

import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.TicketEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PRO-073: log de auditoria de acciones sensibles (altas, bajas, cambios de rol y de estado,
 * aprobaciones, configuracion). Se graba en la misma transaccion que la accion: si la accion
 * falla, no queda registro. Lo leen SUPER_ADMIN y MANAGER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcTemplate jdbc;

    @Value("${app.db.schema}")
    private String schema;

    @Transactional
    public void record(String accion, String entidad, UUID entidadId, String detalle) {
        UUID actor = null;
        String rol = null;
        try {
            actor = CurrentUser.id();
            rol = CurrentUser.role().name();
        } catch (RuntimeException e) {
            // acciones del sistema (jobs) o de plataforma: sin actor de la agencia
        }
        jdbc.update("INSERT INTO " + schema + ".audit_log (actor_id, actor_rol, accion, entidad, entidad_id, detalle) "
                + "VALUES (?, ?, ?, ?, ?, ?)", actor, rol, accion, entidad, entidadId, detalle);
    }

    // --- Tickets (via eventos de dominio) ---

    @EventListener
    public void onTicketStatus(TicketEvents.StatusChanged e) {
        record("TICKET_ESTADO", "TICKET", e.ticket().getId(), e.from() + " -> " + e.to()
                + (e.ticket().getRejectionReason() != null && (e.to().name().equals("RECHAZADA") || e.to().name().equals("CANCELADA"))
                ? " (motivo: " + e.ticket().getRejectionReason() + ")" : ""));
    }

    @EventListener
    public void onTicketAssigned(TicketEvents.ProviderAssigned e) {
        record("TICKET_ASIGNADO", "TICKET", e.ticket().getId(), "proveedor " + e.oldProviderId() + " -> " + e.ticket().getProviderId());
    }

    @EventListener
    public void onTicketCreated(TicketEvents.Created e) {
        record("TICKET_CREADO", "TICKET", e.ticket().getId(), e.ticket().getTitle());
    }
}
