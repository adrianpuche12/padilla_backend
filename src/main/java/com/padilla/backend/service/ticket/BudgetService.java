package com.padilla.backend.service.ticket;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.PropertyRepository;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.NotificationService;
import com.padilla.backend.service.notification.NotificationService.Notice;
import com.padilla.backend.service.settings.AgencySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-053: presupuestos de un ticket (ARQ 4 Mod.3 pasos 4 y 5).
 * - El proveedor asignado envia monto y detalle; se avisa a la inmobiliaria y al propietario.
 * - Aprueba o rechaza el personal de la inmobiliaria o el propietario de la propiedad.
 * - K11: si el monto supera el monto critico de la agencia (agency_settings.monto_critico), solo un
 *   MANAGER o SUPER_ADMIN puede aprobarlo y se escala a los supervisores.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final JdbcTemplate jdbc;
    private final TicketService ticketService;
    private final PropertyRepository propertyRepository;
    private final NotificationService notifications;
    private final AgencySettingsService settings;

    @Value("${app.db.schema}")
    private String schema;

    private static final String COLS = "id, ticket_id AS \"ticketId\", provider_id AS \"providerId\", monto, detalle, status, "
            + "requiere_manager AS \"requiereManager\", decidido_por AS \"decididoPor\", decidido_at AS \"decididoAt\", motivo, "
            + "created_at AS \"createdAt\"";

    @Transactional
    public Map<String, Object> submit(UUID ticketId, BigDecimal monto, String detalle) {
        Ticket t = ticketService.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        if (CurrentUser.role() != Role.PROVIDER || !CurrentUser.id().equals(t.getProviderId())) {
            throw new RbacException("Solo el proveedor asignado puede enviar un presupuesto");
        }
        if (t.getStatus().isFinal()) {
            throw new ConflictException("El ticket esta " + t.getStatus());
        }
        if (monto == null || monto.signum() <= 0) throw new BadRequestException("El monto tiene que ser mayor a cero");
        if (detalle == null || detalle.isBlank()) throw new BadRequestException("El detalle del presupuesto es obligatorio");

        BigDecimal critico = settings.current().getMontoCritico();
        boolean requiereManager = critico != null && monto.compareTo(critico) > 0;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".budgets (id, ticket_id, provider_id, monto, detalle, requiere_manager) VALUES (?, ?, ?, ?, ?, ?)",
                id, ticketId, CurrentUser.id(), monto, detalle.trim(), requiereManager);

        String n = "#" + t.getTicketNumber();
        List<UUID> a = new ArrayList<>(notifications.management());
        propertyRepository.findById(t.getPropertyId()).map(p -> p.getOwnerId()).ifPresent(a::add);
        notifications.notify(a, Notice.of(NotificationType.PRESUPUESTO_RECIBIDO, "Presupuesto para el ticket " + n,
                "Hay un presupuesto de $ " + monto + " para el ticket " + n + " (" + t.getTitle() + ") pendiente de aprobación.",
                "PRESUPUESTO", id));
        if (requiereManager) {
            notifications.notify(notifications.supervisors(), Notice.of(NotificationType.TICKET_ESCALADO,
                    "Presupuesto alto en el ticket " + n, "El presupuesto de $ " + monto + " supera el monto crítico ($ " + critico
                            + ") y necesita aprobación de un MANAGER.", "PRESUPUESTO", id).urgent());
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID ticketId) {
        Ticket t = ticketService.findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        Role role = CurrentUser.role();
        if (role == Role.TENANT) {
            throw new RbacException("El inquilino no ve presupuestos");
        }
        if (role == Role.PROVIDER) {
            return jdbc.queryForList("SELECT " + COLS + " FROM " + schema + ".budgets WHERE ticket_id = ? AND provider_id = ? "
                    + "ORDER BY created_at DESC", t.getId(), CurrentUser.id());
        }
        return jdbc.queryForList("SELECT " + COLS + " FROM " + schema + ".budgets WHERE ticket_id = ? ORDER BY created_at DESC", t.getId());
    }

    @Transactional
    public Map<String, Object> decide(UUID budgetId, boolean aprobar, String motivo) {
        Map<String, Object> b = raw(budgetId);
        UUID ticketId = (UUID) b.get("ticket_id");
        Ticket t = ticketService.findById(ticketId).orElseThrow(() -> new NotFoundException("Presupuesto no encontrado: " + budgetId));
        Role role = CurrentUser.role();
        boolean esPropietario = role == Role.OWNER && propertyRepository.findById(t.getPropertyId())
                .map(p -> CurrentUser.id().equals(p.getOwnerId())).orElse(false);
        if (!role.isManagement() && !esPropietario) {
            throw new RbacException("Solo la inmobiliaria o el propietario deciden un presupuesto");
        }
        if (aprobar && Boolean.TRUE.equals(b.get("requiere_manager")) && role != Role.MANAGER && role != Role.SUPER_ADMIN) {
            throw new RbacException("El presupuesto supera el monto critico: lo tiene que aprobar un MANAGER");
        }
        if (!"PENDIENTE".equals(b.get("status"))) {
            throw new ConflictException("El presupuesto ya fue " + b.get("status"));
        }
        if (!aprobar && (motivo == null || motivo.isBlank())) {
            throw new BadRequestException("El motivo del rechazo es obligatorio");
        }
        jdbc.update("UPDATE " + schema + ".budgets SET status = ?, decidido_por = ?, decidido_at = now(), motivo = ? WHERE id = ?",
                aprobar ? "APROBADO" : "RECHAZADO", CurrentUser.id(), motivo, budgetId);
        String n = "#" + t.getTicketNumber();
        notifications.notify((UUID) b.get("provider_id"), aprobar
                ? Notice.of(NotificationType.PRESUPUESTO_APROBADO, "Presupuesto aprobado " + n,
                        "La inmobiliaria aprobó tu presupuesto para el ticket " + n + ". Ya podés coordinar el trabajo.", "PRESUPUESTO", budgetId)
                : Notice.of(NotificationType.PRESUPUESTO_RECHAZADO, "Presupuesto rechazado " + n,
                        "La inmobiliaria no aprobó tu presupuesto para el ticket " + n + ". Motivo: " + motivo + ".", "PRESUPUESTO", budgetId));
        if (aprobar) {
            notifications.notify(notifications.management(), Notice.of(NotificationType.PRESUPUESTO_APROBADO,
                    "Presupuesto aprobado " + n, "Se aprobó el presupuesto del ticket " + n + ".", "PRESUPUESTO", budgetId).inAppOnly());
        }
        return get(budgetId);
    }

    private Map<String, Object> get(UUID id) {
        return jdbc.queryForMap("SELECT " + COLS + " FROM " + schema + ".budgets WHERE id = ?", id);
    }

    private Map<String, Object> raw(UUID id) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM " + schema + ".budgets WHERE id = ?", id);
        if (r.isEmpty()) throw new NotFoundException("Presupuesto no encontrado: " + id);
        return r.get(0);
    }
}
