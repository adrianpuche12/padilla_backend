package com.padilla.backend.controller.auth;

import com.padilla.backend.dto.auth.ChangePasswordRequest;
import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.RefreshRequest;
import com.padilla.backend.dto.auth.TokenResponse;
import com.padilla.backend.entity.User;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminService keycloakAdminService;
    private final UserRepository userRepository;

    // Requisitos de seguridad del password
    private static final int MIN_LENGTH        = 8;
    private static final Pattern HAS_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_NUMBER    = Pattern.compile(".*[0-9].*");
    private static final Pattern HAS_SYMBOL    = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    /**
     * POST /api/auth/login
     * Autentica un usuario y devuelve los tokens JWT.
     * Si es el primer login, incluye "first_login: true" en la respuesta.
     * Si el acceso temporal expiró, devuelve 403 ACCESS_EXPIRED.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            TokenResponse tokenResponse = keycloakAuthService.login(loginRequest);

            // Verificar si es primer login
            Optional<User> userOpt = userRepository.findByEmail(loginRequest.getUsername());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.isFirstLogin()) {
                    OffsetDateTime expiresAt = user.getPasswordResetExpiresAt();
                    if (expiresAt != null && OffsetDateTime.now().isAfter(expiresAt)) {
                        log.warn("Acceso temporal expirado para usuario: {}", loginRequest.getUsername());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of(
                                        "error", "ACCESS_EXPIRED",
                                        "message", "Tu acceso temporal ha vencido. Contactá al administrador para recibir un nuevo acceso."
                                ));
                    }
                    tokenResponse.setFirstLogin(true);
                }
            }

            return ResponseEntity.ok(tokenResponse);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Credenciales invalidas para usuario: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales invalidas"));
        } catch (HttpClientErrorException e) {
            log.error("Error de Keycloak: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Error de autenticacion", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado en login: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * PUT /api/auth/change-password
     * Cambia el password en el primer login obligatorio.
     * Requiere autenticacion (Bearer token del primer login).
     */
    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            String newPassword = request.getNewPassword();

            // Validar requisitos del nuevo password
            String validationError = validatePasswordStrength(newPassword);
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_PASSWORD", "message", validationError));
            }

            // Obtener usuario desde el JWT (sub = UUID del usuario)
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

            // Actualizar password en Keycloak
            keycloakAdminService.setPermanentPasswordById(userId.toString(), newPassword);

            // Marcar primer login como completado en DB
            user.setFirstLogin(false);
            user.setPasswordResetExpiresAt(null);
            userRepository.save(user);

            log.info("Password cambiado exitosamente para usuario: {}", user.getEmail());
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente"));

        } catch (Exception e) {
            log.error("Error al cambiar password: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar la contraseña"));
        }
    }

    /**
     * POST /api/auth/refresh
     * Renueva los tokens usando el refresh_token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest refreshRequest) {
        try {
            TokenResponse tokenResponse = keycloakAuthService.refresh(refreshRequest);
            return ResponseEntity.ok(tokenResponse);
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Refresh token invalido o expirado");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token invalido o expirado"));
        } catch (HttpClientErrorException e) {
            log.error("Error de Keycloak al renovar token: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Error al renovar token", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado en refresh: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    /**
     * POST /api/auth/logout
     * Cierra la sesion revocando el refresh_token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshRequest refreshRequest) {
        try {
            keycloakAuthService.logout(refreshRequest);
            return ResponseEntity.ok(Map.of("message", "Sesion cerrada exitosamente"));
        } catch (HttpClientErrorException e) {
            log.error("Error de Keycloak en logout: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "Error al cerrar sesion", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado en logout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }

    // ---------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------

    private String validatePasswordStrength(String password) {
        if (password == null || password.length() < MIN_LENGTH)
            return "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres";
        if (!HAS_UPPERCASE.matcher(password).matches())
            return "La contraseña debe contener al menos 1 letra mayúscula";
        if (!HAS_NUMBER.matcher(password).matches())
            return "La contraseña debe contener al menos 1 número";
        if (!HAS_SYMBOL.matcher(password).matches())
            return "La contraseña debe contener al menos 1 símbolo (!@#$%^&*)";
        return null;
    }
}
