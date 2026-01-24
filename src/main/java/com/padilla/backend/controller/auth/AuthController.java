package com.padilla.backend.controller.auth;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.RefreshRequest;
import com.padilla.backend.dto.auth.TokenResponse;
import com.padilla.backend.service.auth.KeycloakAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;

    /**
     * POST /api/auth/login
     * Autentica un usuario y devuelve los tokens JWT
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            TokenResponse tokenResponse = keycloakAuthService.login(loginRequest);
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
}
