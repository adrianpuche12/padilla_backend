package com.padilla.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("name", jwt.getClaimAsString("name"));

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("message", "Bienvenido al dashboard");
        dashboard.put("username", jwt.getClaimAsString("preferred_username"));

        // Extraer roles del token
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            dashboard.put("roles", realmAccess.get("roles"));
        } else {
            dashboard.put("roles", List.of());
        }

        // Features disponibles para el usuario
        Map<String, Boolean> features = new HashMap<>();
        features.put("viewLeads", true);
        features.put("viewSellers", true);
        features.put("viewSources", true);
        features.put("viewStatistics", true);
        dashboard.put("features", features);

        return ResponseEntity.ok(dashboard);
    }
}
