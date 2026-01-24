package com.padilla.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "padilla-backend",
                "module", "admin-dashboard",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Endpoint protegido solo para ADMIN.
     * Retorna informacion del dashboard administrativo.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "message", "Bienvenido al panel de administracion",
                "user", jwt.getClaimAsString("preferred_username"),
                "access", "ADMIN",
                "timestamp", LocalDateTime.now().toString(),
                "features", Map.of(
                        "gestionUsuarios", true,
                        "reportes", true,
                        "configuracion", true
                )
        ));
    }

}
