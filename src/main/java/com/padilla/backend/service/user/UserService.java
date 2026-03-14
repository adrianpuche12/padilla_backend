package com.padilla.backend.service.user;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${spring.jpa.properties.hibernate.default_schema:padilla_dev}")
    private String schema;

    // Jerarquia de roles: menor numero = mayor privilegio
    private static final Map<Role, Integer> ROLE_LEVEL = Map.of(
            Role.SUPER_ADMIN, 0,
            Role.MANAGER,     1,
            Role.ADMIN,       2,
            Role.OWNER,       3,
            Role.TENANT,      3,
            Role.PROVIDER,    3
    );

    public List<User> findAll() {
        Role callerRole = getCurrentUserRole();
        int callerLevel = ROLE_LEVEL.get(callerRole);
        return userRepository.findAll().stream()
                .filter(u -> ROLE_LEVEL.getOrDefault(u.getRole(), 99) > callerLevel)
                .collect(Collectors.toList());
    }

    public List<User> findByRole(Role role) {
        Role callerRole = getCurrentUserRole();
        int callerLevel = ROLE_LEVEL.get(callerRole);
        int targetLevel = ROLE_LEVEL.getOrDefault(role, 99);
        if (targetLevel <= callerLevel) {
            return List.of();
        }
        return userRepository.findByRole(role);
    }

    public Optional<User> findById(UUID id) {
        Role callerRole = getCurrentUserRole();
        int callerLevel = ROLE_LEVEL.get(callerRole);
        return userRepository.findById(id)
                .filter(u -> ROLE_LEVEL.getOrDefault(u.getRole(), 99) > callerLevel);
    }

    @Transactional
    public User createUser(User user) {
        Role callerRole = getCurrentUserRole();
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
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        user.setName(name);
        user.setPhone(phone);
        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());
        validateNotSelf(user.getEmail());

        user.setActive(false);
        userRepository.save(user);
        keycloakAdminService.disableUser(user.getEmail());
    }

    @Transactional
    public CreateUserResult createUserFull(User user) {
        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        String keycloakId = keycloakAdminService.createUser(user.getName(), user.getEmail(), user.getRole());

        UUID newId = null;
        if (keycloakId != null) {
            newId = UUID.fromString(keycloakId);
        }

        // JdbcTemplate INSERT para evitar conflicto de ciclo de vida JPA:
        // @GeneratedValue hace que Hibernate trate cualquier entidad con ID != null
        // como "detached", tanto merge() como persist() fallan. JDBC lo evita completamente.
        UUID createdBy = null;
        try {
            createdBy = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (IllegalArgumentException ignored) {}

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
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        user.setActive(true);
        userRepository.save(user);
        keycloakAdminService.enableUser(user.getEmail());
        return user;
    }

    @Transactional
    public String resetPassword(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
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
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        Role callerRole = getCurrentUserRole();
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
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());
        validateNotSelf(user.getEmail());

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

    private Role getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(r -> {
                    try { Role.valueOf(r); return true; } catch (Exception e) { return false; }
                })
                .map(Role::valueOf)
                .min((a, b) -> ROLE_LEVEL.get(a) - ROLE_LEVEL.get(b))
                .orElseThrow(() -> new RbacException("No valid role found in token"));
    }

    private void validateCanManage(Role caller, Role target) {
        int callerLevel = ROLE_LEVEL.get(caller);
        int targetLevel = ROLE_LEVEL.get(target);
        if (callerLevel >= targetLevel) {
            throw new RbacException(
                    "Role " + caller + " cannot manage role " + target
            );
        }
    }

    private void validateNotSelf(String targetEmail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String callerUsername = auth.getName();
        if (callerUsername.equalsIgnoreCase(targetEmail)) {
            throw new RbacException("You cannot deactivate your own account");
        }
    }
}
