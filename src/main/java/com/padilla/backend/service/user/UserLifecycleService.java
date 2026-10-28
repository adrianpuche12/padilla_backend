package com.padilla.backend.service.user;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PRO-033 / PRO-034: efectos de la baja logica y reasignacion de actores (ARQ 7.2, reglas 4 y 5).
 * Nunca se borran datos: se reasigna, se marca o se cancela, y queda el historial.
 *
 * - ADMIN / MANAGER: los usuarios que gestionaba (created_by) pasan a otro activo del mismo rol;
 *   si no hay, quedan sin gestor (visibles para los niveles superiores).
 * - OWNER: sus contratos activos quedan "propietario inactivo".
 * - TENANT: sus tickets abiertos se cancelan ("baja del inquilino") y sus contratos activos quedan
 *   "inquilino inactivo".
 * - PROVIDER: sus tickets no terminados vuelven a la cola (sin proveedor, ABIERTA) con registro en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLifecycleService {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    @Value("${app.db.schema}")
    private String schema;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onDeactivated(User user) {
        UUID actor = CurrentUser.id();
        jdbc.update("UPDATE " + schema + ".users SET deactivated_at = NOW(), deactivated_by = ? WHERE id = ?", actor, user.getId());
        switch (user.getRole()) {
            case SUPER_ADMIN, MANAGER, ADMIN -> reassignManaged(user, replacementFor(user).orElse(null));
            case OWNER -> jdbc.update("UPDATE " + schema + ".contracts SET owner_inactive = TRUE "
                    + "WHERE owner_id = ? AND status = 'ACTIVE'", user.getId());
            case TENANT -> {
                int cancelados = jdbc.update("UPDATE " + schema + ".tickets SET status = 'CANCELADA', "
                        + "rejection_reason = 'Cancelado por baja del inquilino', closed_at = NOW(), updated_at = NOW() "
                        + "WHERE tenant_id = ? AND status IN ('ABIERTA', 'EN_PROCESO')", user.getId());
                jdbc.update("UPDATE " + schema + ".contracts SET tenant_inactive = TRUE "
                        + "WHERE tenant_id = ? AND status = 'ACTIVE'", user.getId());
                log.info("Baja del inquilino {}: {} tickets cancelados", user.getEmail(), cancelados);
            }
            case PROVIDER -> {
                jdbc.update("INSERT INTO " + schema + ".ticket_provider_log (ticket_id, changed_by, old_provider_id, new_provider_id) "
                        + "SELECT id, ?, provider_id, NULL FROM " + schema + ".tickets "
                        + "WHERE provider_id = ? AND status IN ('ABIERTA', 'EN_PROCESO')", actor, user.getId());
                int devueltos = jdbc.update("UPDATE " + schema + ".tickets SET provider_id = NULL, status = 'ABIERTA', updated_at = NOW() "
                        + "WHERE provider_id = ? AND status IN ('ABIERTA', 'EN_PROCESO')", user.getId());
                log.info("Baja del proveedor {}: {} tickets vuelven a la cola", user.getEmail(), devueltos);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onReactivated(User user) {
        jdbc.update("UPDATE " + schema + ".users SET deactivated_at = NULL, deactivated_by = NULL WHERE id = ?", user.getId());
        if (user.getRole() == Role.OWNER) {
            jdbc.update("UPDATE " + schema + ".contracts SET owner_inactive = FALSE WHERE owner_id = ?", user.getId());
        } else if (user.getRole() == Role.TENANT) {
            jdbc.update("UPDATE " + schema + ".contracts SET tenant_inactive = FALSE WHERE tenant_id = ?", user.getId());
        }
    }

    /**
     * PRO-034: reasignacion manual. Los actores que gestionaba fromId pasan a toId. Solo un nivel
     * superior puede hacerlo (SUPER_ADMIN reasigna entre MANAGERs, MANAGER entre ADMINs) y ambos
     * tienen que tener el mismo rol.
     */
    @Transactional
    public int reassign(UUID fromId, UUID toId) {
        User from = userRepository.findById(fromId).orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + fromId));
        User to = userRepository.findById(toId).orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + toId));
        if (!from.getRole().isManagement()) {
            throw new BadRequestException("Solo se reasignan los actores de un usuario de sistema (MANAGER o ADMIN)");
        }
        if (from.getRole() != to.getRole()) {
            throw new BadRequestException("Solo se puede reasignar a otro usuario con el mismo rol (" + from.getRole() + ")");
        }
        if (!to.isActive()) {
            throw new BadRequestException("El usuario destino esta desactivado");
        }
        if (!CurrentUser.role().canManage(from.getRole())) {
            throw new RbacException("El rol " + CurrentUser.role() + " no puede reasignar actores de un " + from.getRole());
        }
        return reassignManaged(from, to);
    }

    private int reassignManaged(User from, User to) {
        int n = jdbc.update("UPDATE " + schema + ".users SET created_by = ? WHERE created_by = ? AND id <> ?",
                to == null ? null : to.getId(), from.getId(), from.getId());
        log.info("{} actores de {} reasignados a {}", n, from.getEmail(), to == null ? "(sin gestor)" : to.getEmail());
        return n;
    }

    private Optional<User> replacementFor(User user) {
        List<User> candidatos = userRepository.findByRole(user.getRole()).stream()
                .filter(u -> u.isActive() && !u.getId().equals(user.getId()))
                .toList();
        return candidatos.stream().findFirst();
    }
}
