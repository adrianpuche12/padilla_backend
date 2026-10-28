package com.padilla.backend.controller;


import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.dto.user.CreateUserRequest;
import com.padilla.backend.dto.user.CreateUserResponse;
import com.padilla.backend.dto.user.UpdateUserRequest;
import com.padilla.backend.dto.user.UserDTO;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final com.padilla.backend.repository.UserRepository userRepository;

    private final com.padilla.backend.service.user.UserLifecycleService userLifecycleService;

    private final UserService userService;

    // -------------------------------------------------------
    // Endpoints legacy — usados por el frontend actual (n8n)
    // -------------------------------------------------------

    @GetMapping("/api/user/profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("name", jwt.getClaimAsString("name"));
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/api/user/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("message", "Bienvenido al dashboard");
        dashboard.put("username", jwt.getClaimAsString("preferred_username"));

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            dashboard.put("roles", realmAccess.get("roles"));
        } else {
            dashboard.put("roles", List.of());
        }

        Map<String, Boolean> features = new HashMap<>();
        features.put("viewLeads", true);
        features.put("viewSellers", true);
        features.put("viewSources", true);
        features.put("viewStatistics", true);
        dashboard.put("features", features);

        return ResponseEntity.ok(dashboard);
    }

    // -------------------------------------------------------
    // Endpoints CRUD de usuarios — modulo Identity & Actors
    // -------------------------------------------------------

    @GetMapping("/api/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<?> getUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {

        // PRO-040: pagina de usuarios de sistema con busqueda
        if ("system".equalsIgnoreCase(scope) && page != null) {
            com.padilla.backend.security.CurrentUser.requireManagement();
            var pagina = userRepository.findAll(com.padilla.backend.service.listing.Listings.users(
                            List.of(Role.SUPER_ADMIN, Role.MANAGER, Role.ADMIN), role, active, q),
                    com.padilla.backend.util.Paging.request(page, size, sort, java.util.Set.of("name", "email", "role", "createdAt"),
                            org.springframework.data.domain.Sort.by("name")));
            return ResponseEntity.ok(com.padilla.backend.dto.PageResponse.of(pagina, u -> {
                UserDTO dto = toDTO(u);
                dto.setCanManage(userService.canManage(u));
                return dto;
            }));
        }

        // PRO-013: ?scope=system = usuarios de sistema con canManage por fila (pantalla Usuarios)
        if ("system".equalsIgnoreCase(scope)) {
            return ResponseEntity.ok(userService.findSystemUsers().stream()
                    .map(u -> {
                        UserDTO dto = toDTO(u);
                        dto.setCanManage(userService.canManage(u));
                        return dto;
                    })
                    .collect(Collectors.toList()));
        }

        List<User> users = (role != null)
                ? userService.findByRole(role)
                : userService.findAll();

        return ResponseEntity.ok(users.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/api/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<UserDTO> getUser(@PathVariable UUID id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(toDTO(user)))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());

        UserService.CreateUserResult result = userService.createUserFull(user);
        CreateUserResponse response = new CreateUserResponse(toDTO(result.user()), result.temporaryPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/api/users/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<UserDTO> reactivateUser(@PathVariable UUID id) {
        User reactivated = userService.reactivateUser(id);
        return ResponseEntity.ok(toDTO(reactivated));
    }

    @PostMapping("/api/users/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable UUID id) {
        String temporaryPassword = userService.resetPassword(id);
        return ResponseEntity.ok(Map.of("temporaryPassword", temporaryPassword));
    }

    @PatchMapping("/api/users/{id}/resend-access")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> resendAccess(@PathVariable UUID id) {
        userService.resendAccess(id);
        return ResponseEntity.ok(Map.of("message", "Acceso reenviado exitosamente"));
    }

    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        User updated = userService.updateUser(id, request.getName(), request.getPhone());
        return ResponseEntity.ok(toDTO(updated));
    }

    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok().build();
    }

    /** PRO-034: los actores que gestionaba {id} pasan a {to} (mismo rol, lo hace un nivel superior). */
    @PatchMapping("/api/users/{id}/reassign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> reassign(@PathVariable UUID id, @RequestParam UUID to) {
        int n = userLifecycleService.reassign(id, to);
        return ResponseEntity.ok(Map.of("reasignados", n));
    }

    @DeleteMapping("/api/users/{id}/permanent")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteUserPermanently(@PathVariable UUID id) {
        userService.deleteUserPermanently(id);
        return ResponseEntity.noContent().build();
    }


    private UserDTO toDTO(User user) {
        return new UserDTO(
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
                null
        );
    }
}
