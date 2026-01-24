package com.padilla.backend.controller.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para endpoints de usuario.
 * Accesible para roles USER y ADMIN.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /**
     * Obtiene el perfil del usuario autenticado.
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "username", jwt.getClaimAsString("preferred_username"),
                "email", jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : "",
                "name", jwt.getClaimAsString("name") != null ? jwt.getClaimAsString("name") : "",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Obtiene el dashboard del usuario con sus roles.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            JwtAuthenticationToken authentication) {

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "message", "Bienvenido a tu dashboard",
                "user", jwt.getClaimAsString("preferred_username"),
                "roles", roles,
                "timestamp", LocalDateTime.now().toString(),
                "features", Map.of(
                        "verPerfil", true,
                        "verLeads", true,
                        "editarPerfil", true
                )
        ));
    }

}
