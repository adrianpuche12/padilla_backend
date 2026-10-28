package com.padilla.backend.service.notification;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.NotificationType;
import com.padilla.backend.enums.Role;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * PRO-060: punto unico para notificar. Guarda en notification_log (bandeja in-app + log permanente)
 * y, si corresponde, manda el mail. Texto institucional: hacia afuera habla "la inmobiliaria",
 * nunca un empleado (ARQ 4.1, notificacion anonima).
 *
 * Corre dentro de la transaccion de quien llama (la agencia la toma de ahi, via DEFAULT de agency_id).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationPreferences preferences;

    @Value("${app.db.schema}")
    private String schema;

    public record Notice(NotificationType tipo, String titulo, String mensaje, String referenciaTipo, UUID referenciaId,
                         boolean urgente, boolean email) {
        public static Notice of(NotificationType tipo, String titulo, String mensaje, String refTipo, UUID refId) {
            return new Notice(tipo, titulo, mensaje, refTipo, refId, false, true);
        }

        public Notice urgent() {
            return new Notice(tipo, titulo, mensaje, referenciaTipo, referenciaId, true, email);
        }

        public Notice inAppOnly() {
            return new Notice(tipo, titulo, mensaje, referenciaTipo, referenciaId, urgente, false);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public int notify(Collection<UUID> userIds, Notice n) {
        Set<UUID> destinatarios = new LinkedHashSet<>(userIds);
        destinatarios.remove(null);
        int creadas = 0;
        for (UUID userId : destinatarios) {
            int filas = jdbc.update("INSERT INTO " + schema + ".notification_log (user_id, tipo, titulo, mensaje, referencia_tipo, "
                            + "referencia_id, prioridad) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    userId, n.tipo().name(), n.titulo(), n.mensaje(), n.referenciaTipo(), n.referenciaId(),
                    n.urgente() ? "URGENTE" : "NORMAL");
            if (filas == 0) {
                continue; // ya se habia notificado este evento
            }
            creadas++;
            if (n.email() && preferences.wantsEmail(userId, n.tipo())) {
                userRepository.findById(userId).filter(User::isActive).ifPresent(u ->
                        emailService.sendNotificationEmail(u.getEmail(), u.getName(), n.titulo(), n.mensaje()));
            }
        }
        return creadas;
    }

    public int notify(UUID userId, Notice n) {
        return notify(List.of(userId), n);
    }

    /** Personal de la inmobiliaria que gestiona la operacion diaria (ADMIN, MANAGER, SUPER_ADMIN activos). */
    public List<UUID> management() {
        return activeWithRoles(Role.SUPER_ADMIN, Role.MANAGER, Role.ADMIN);
    }

    /** Supervision: MANAGER y SUPER_ADMIN activos (escalamientos, SLA, feedback negativo). */
    public List<UUID> supervisors() {
        return activeWithRoles(Role.SUPER_ADMIN, Role.MANAGER);
    }

    private List<UUID> activeWithRoles(Role... roles) {
        return userRepository.findByRoleIn(List.of(roles)).stream()
                .filter(User::isActive).map(User::getId).filter(Objects::nonNull).toList();
    }
}
