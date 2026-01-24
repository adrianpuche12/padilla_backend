package com.padilla.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SecureController {

    @GetMapping("/secure")
    public ResponseEntity<Map<String, Object>> secureEndpoint(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Acceso permitido - Endpoint protegido");
        response.put("user", jwt.getSubject());
        response.put("email", jwt.getClaimAsString("email"));
        response.put("roles", jwt.getClaimAsStringList("roles"));
        response.put("tokenExpira", jwt.getExpiresAt());

        return ResponseEntity.ok(response);
    }
}
