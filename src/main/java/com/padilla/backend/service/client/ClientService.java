package com.padilla.backend.service.client;

import com.padilla.backend.dto.client.ClientDTO;
import com.padilla.backend.dto.client.CreateClientRequest;
import com.padilla.backend.dto.client.UpdateClientRequest;
import com.padilla.backend.entity.ClientProfile;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.PersonaType;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.ClientProfileRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    @Value("${spring.jpa.properties.hibernate.default_schema:padilla_dev}")
    private String schema;

    private static final Set<Role> CLIENT_ROLES = Set.of(Role.OWNER, Role.TENANT, Role.PROVIDER);

    private static final Map<Role, Integer> ROLE_LEVEL = Map.of(
            Role.SUPER_ADMIN, 0,
            Role.MANAGER,     1,
            Role.ADMIN,       2,
            Role.OWNER,       3,
            Role.TENANT,      3,
            Role.PROVIDER,    3
    );

    public List<ClientDTO> findAll() {
        return userRepository.findByRoleIn(List.of(Role.OWNER, Role.TENANT, Role.PROVIDER))
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ClientDTO> findByRole(Role role) {
        if (!CLIENT_ROLES.contains(role)) return List.of();
        return userRepository.findByRole(role).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Optional<ClientDTO> findById(UUID id) {
        return userRepository.findById(id)
                .filter(u -> CLIENT_ROLES.contains(u.getRole()))
                .map(this::toDTO);
    }

    @Transactional
    public ClientDTO createClient(CreateClientRequest request) {
        Role callerRole = getCurrentUserRole();

        // Solo se permiten roles de cliente en este endpoint
        if (!CLIENT_ROLES.contains(request.getRole())) {
            throw new RbacException("Solo se pueden crear clientes con roles OWNER, TENANT o PROVIDER");
        }

        // Validar cascada de permisos
        validateCanManage(callerRole, request.getRole());

        // 1. Crear en Keycloak
        String keycloakId = keycloakAdminService.createUser(
                request.getName(), request.getEmail(), request.getRole());

        UUID newId = (keycloakId != null) ? UUID.fromString(keycloakId) : UUID.randomUUID();

        // 2. Obtener UUID del creador
        UUID createdBy = null;
        try {
            createdBy = UUID.fromString(
                    SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (IllegalArgumentException ignored) {}

        // 3. Insertar en tabla users (mismo patrón que UserService.createUserFull)
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".users " +
                "(id, name, email, phone, role, active, created_by, created_at, first_login, password_reset_expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), TRUE, NOW() + INTERVAL '24 hours')",
                newId,
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getRole().name(),
                true,
                createdBy
        );

        // 4. Insertar en tabla client_profiles
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".client_profiles " +
                "(id, user_id, persona_type, specialty, document_type, document_number, cuit_cuil, " +
                " address, city, province, iva_condition, notes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID(),
                newId,
                request.getPersonaType() != null ? request.getPersonaType().name() : PersonaType.FISICA.name(),
                request.getSpecialty() != null ? request.getSpecialty().name() : null,
                request.getDocumentType(),
                request.getDocumentNumber(),
                request.getCuitCuil(),
                request.getAddress(),
                request.getCity(),
                request.getProvince(),
                request.getIvaCondition(),
                request.getNotes()
        );

        // 5. Generar password temporal y enviar email de bienvenida
        String tempPassword = null;
        if (keycloakId != null) {
            tempPassword = keycloakAdminService.generateAndSetTemporaryPassword(keycloakId);
        }
        emailService.sendWelcomeEmail(request.getEmail(), request.getName(), tempPassword);

        // 6. Retornar DTO completo con password temporal incluido
        User saved = userRepository.findById(newId)
                .orElseThrow(() -> new RuntimeException("Error al cargar el cliente recién creado"));
        ClientDTO dto = toDTO(saved);
        dto.setTemporaryPassword(tempPassword);
        return dto;
    }

    @Transactional
    public ClientDTO updateClient(UUID id, UpdateClientRequest request) {
        User user = userRepository.findById(id)
                .filter(u -> CLIENT_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        // Actualizar datos básicos en users
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        // Actualizar o crear perfil extendido
        ClientProfile profile = clientProfileRepository.findByUserId(id)
                .orElseGet(() -> {
                    ClientProfile p = new ClientProfile();
                    p.setUserId(id);
                    return p;
                });

        profile.setPersonaType(request.getPersonaType());
        profile.setSpecialty(request.getSpecialty());
        profile.setDocumentType(request.getDocumentType());
        profile.setDocumentNumber(request.getDocumentNumber());
        profile.setCuitCuil(request.getCuitCuil());
        profile.setAddress(request.getAddress());
        profile.setCity(request.getCity());
        profile.setProvince(request.getProvince());
        profile.setIvaCondition(request.getIvaCondition());
        profile.setNotes(request.getNotes());
        clientProfileRepository.save(profile);

        return toDTO(userRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void deactivateClient(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> CLIENT_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        user.setActive(false);
        userRepository.save(user);
        keycloakAdminService.disableUser(user.getEmail());
    }

    @Transactional
    public void deleteClient(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> CLIENT_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        // 1. Eliminar perfil extendido
        clientProfileRepository.findByUserId(id).ifPresent(clientProfileRepository::delete);

        // 2. Eliminar registro de usuario en DB
        userRepository.delete(user);

        // 3. Eliminar en Keycloak (por email, más robusto que por UUID)
        keycloakAdminService.deleteUserByEmail(user.getEmail());
    }

    @Transactional
    public ClientDTO reactivateClient(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> CLIENT_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + id));

        Role callerRole = getCurrentUserRole();
        validateCanManage(callerRole, user.getRole());

        user.setActive(true);
        userRepository.save(user);
        keycloakAdminService.enableUser(user.getEmail());
        return toDTO(user);
    }

    // --- Helpers privados ---

    private ClientDTO toDTO(User user) {
        ClientProfile profile = clientProfileRepository.findByUserId(user.getId()).orElse(null);
        return new ClientDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getCreatedBy(),
                user.getCreatedAt(),
                user.isFirstLogin(),
                user.getPasswordResetExpiresAt(),
                profile != null ? profile.getId() : null,
                profile != null ? profile.getLegacyId() : null,
                profile != null ? profile.getPersonaType() : null,
                profile != null ? profile.getSpecialty() : null,
                profile != null ? profile.getDocumentType() : null,
                profile != null ? profile.getDocumentNumber() : null,
                profile != null ? profile.getCuitCuil() : null,
                profile != null ? profile.getAddress() : null,
                profile != null ? profile.getCity() : null,
                profile != null ? profile.getProvince() : null,
                profile != null ? profile.getIvaCondition() : null,
                profile != null ? profile.getNotes() : null,
                null  // temporaryPassword — solo se setea al crear
        );
    }

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
            throw new RbacException("El rol " + caller + " no puede gestionar el rol " + target);
        }
    }
}
