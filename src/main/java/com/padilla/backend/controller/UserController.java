package com.padilla.backend.controller;

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

    private final UserService userService;

    // -------------------------------------------------------
    // Endpoints legacy — usados por el frontend actual (n8n)
    // -------------------------------------------------------

    @GetMapping("/api/user/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("name", jwt.getClaimAsString("name"));
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/api/user/dashboard")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
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
    public ResponseEntity<List<UserDTO>> getUsers(
            @RequestParam(required = false) Role role) {

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
                .orElse(ResponseEntity.notFound().build());
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
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
                user.getCreatedAt()
        );
    }
}
