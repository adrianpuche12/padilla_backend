package com.padilla.backend.service.user;


import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final com.padilla.backend.service.settings.AgencySettingsService agencySettingsService;

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${app.db.schema}")
    private String schema;


    public List<User> findAll() {
        Role callerRole = CurrentUser.role();
        int callerLevel = callerRole.level();
        return userRepository.findAll().stream()
                .filter(u -> u.getRole().level() > callerLevel)
                .collect(Collectors.toList());
    }

    /**
     * PRO-013: usuarios de sistema (SUPER_ADMIN, MANAGER, ADMIN) para la pantalla Usuarios.
     * Se listan todos los de la inmobiliaria (incluidos pares y superiores, en solo lectura);
     * canManage(...) dice sobre cuales puede actuar quien consulta. Antes findAll() devolvia solo
     * roles estrictamente inferiores y el frontend se quedaba con los de sistema: un ADMIN veia la lista vacia.
     */
    public List<User> findSystemUsers() {
        CurrentUser.requireManagement();
        return userRepository.findByRoleIn(List.of(Role.SUPER_ADMIN, Role.MANAGER, Role.ADMIN)).stream()
                .sorted(Comparator.comparingInt((User u) -> u.getRole().level()).thenComparing(User::getName))
                .toList();
    }

    /** PRO-013: quien consulta puede gestionar a este usuario (cascada y no a si mismo). */
    public boolean canManage(User target) {
        return CurrentUser.role().canManage(target.getRole()) && !CurrentUser.id().equals(target.getId());
    }

    public List<User> findByRole(Role role) {
        Role callerRole = CurrentUser.role();
        int callerLevel = callerRole.level();
        int targetLevel = role.level();
        if (targetLevel <= callerLevel) {
            return List.of();
        }
        return userRepository.findByRole(role);
    }

    public Optional<User> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        int callerLevel = callerRole.level();
        return userRepository.findById(id)
                .filter(u -> u.getRole().level() > callerLevel);
    }

    @Transactional
    public User createUser(User user) {
        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());
        // Crear en Keycloak primero para obtener su UUID y usarlo como ID en DB.
        // Esto garantiza que DB ID == Keycloak sub, necesario para que RBAC funcione.
        String keycloakId = keycloakAdminService.createUser(user.getName(), user.getEmail(), user.getRole());
        if (keycloakId != null) {
            user.setId(UUID.fromString(keycloakId));
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(UUID id, String name, String phone) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());

        user.setName(name);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());
        validateNotSelf(user.getId());

        user.setActive(false);
        userRepository.save(user);
        keycloakAdminService.disableUser(user.getEmail());
    }

    @Transactional
    public CreateUserResult createUserFull(User user) {
        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());
        // PRO-025: limite de usuarios ADMIN del plan. U5: un unico SUPER_ADMIN por inmobiliaria.
        if (user.getRole() == Role.ADMIN) {
            agencySettingsService.checkCanAddAdmin();
        }
        if (user.getRole() == Role.SUPER_ADMIN && !userRepository.findByRoleIn(List.of(Role.SUPER_ADMIN)).isEmpty()) {
            throw new ConflictException("La inmobiliaria ya tiene un SUPER_ADMIN");
        }

        String keycloakId = keycloakAdminService.createUser(user.getName(), user.getEmail(), user.getRole());

        UUID newId = null;
        if (keycloakId != null) {
            newId = UUID.fromString(keycloakId);
        }

        // JdbcTemplate INSERT para evitar conflicto de ciclo de vida JPA:
        // @GeneratedValue hace que Hibernate trate cualquier entidad con ID != null
        // como "detached", tanto merge() como persist() fallan. JDBC lo evita completamente.
        UUID createdBy = CurrentUser.id();

        jdbcTemplate.update(
                "INSERT INTO " + schema + ".users (id, name, email, phone, role, active, created_by, created_at, first_login, password_reset_expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), TRUE, NOW() + INTERVAL '24 hours')",
                newId,
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                true,
                createdBy
        );

        User saved = userRepository.findById(newId)
                .orElseThrow(() -> new RuntimeException("Failed to load user after creation"));

        String tempPassword = null;
        if (keycloakId != null) {
            tempPassword = keycloakAdminService.generateAndSetTemporaryPassword(keycloakId);
        }

        emailService.sendWelcomeEmail(saved.getEmail(), saved.getName(), tempPassword);

        return new CreateUserResult(saved, tempPassword);
    }

    @Transactional
    public User reactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());

        user.setActive(true);
        userRepository.save(user);
        keycloakAdminService.enableUser(user.getEmail());
        return user;
    }

    @Transactional
    public String resetPassword(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
        // PRO-016: antes no se validaba la cascada (un SUPER_ADMIN podia resetear a otro SUPER_ADMIN)
        validateCanManage(CurrentUser.role(), user.getRole());
        validateNotSelf(user.getId());
        String tempPassword = keycloakAdminService.resetPasswordByEmail(user.getEmail());
        user.setFirstLogin(true);
        user.setPasswordResetExpiresAt(OffsetDateTime.now().plusHours(24));
        userRepository.save(user);
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), tempPassword);
        return tempPassword;
    }

    @Transactional
    public void resendAccess(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());

        String tempPassword = keycloakAdminService.resetPasswordByEmail(user.getEmail());
        user.setFirstLogin(true);
        user.setPasswordResetExpiresAt(OffsetDateTime.now().plusHours(24));
        userRepository.save(user);
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), tempPassword);
    }

    @Transactional
    public void deleteUserPermanently(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));

        Role callerRole = CurrentUser.role();
        validateCanManage(callerRole, user.getRole());
        validateNotSelf(user.getId());

        // Se elimina primero de la DB (dentro de la transaccion).
        // Si la llamada a Keycloak falla despues, Spring hace rollback
        // y el usuario queda intacto en la DB. Orden intencionado.
        // Se usa deleteUserByEmail (no deleteUserById) para manejar correctamente
        // usuarios con UUID desincronizado entre DB y Keycloak.
        userRepository.deleteById(id);

        keycloakAdminService.deleteUserByEmail(user.getEmail());
    }

    public record CreateUserResult(User user, String temporaryPassword) {}

    // --- RBAC helpers ---


    private void validateCanManage(Role caller, Role target) {
        int callerLevel = caller.level();
        int targetLevel = target.level();
        if (callerLevel >= targetLevel) {
            throw new RbacException("El rol " + caller + " no puede gestionar usuarios con rol " + target);
        }
    }

    /**
     * PRO-016: compara por id (sub del JWT). Antes comparaba el sub con el email, nunca coincidia
     * y un administrador podia desactivarse o borrarse a si mismo.
     */
    private void validateNotSelf(UUID targetId) {
        if (CurrentUser.id().equals(targetId)) {
            throw new RbacException("No podes desactivar, eliminar ni resetear tu propia cuenta");
        }
    }
}
