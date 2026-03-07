package com.padilla.backend.service.user;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

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
        return userRepository.findAll();
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User createUser(User user) {
        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());
        User saved = userRepository.save(user);
        keycloakAdminService.createUser(saved.getName(), saved.getEmail(), saved.getRole());
        return saved;
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
