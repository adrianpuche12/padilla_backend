package com.padilla.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PRO-007: solo cuentan los roles del realm y los del cliente de Propero. */
class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter("padilla_frontend");

    @Test
    void ignoraRolesDeOtrosClientesDelRealm() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s").issuedAt(Instant.now())
                .claim("realm_access", Map.of("roles", List.of("offline_access")))
                .claim("resource_access", Map.of(
                        "padilla_frontend", Map.of("roles", List.of("TENANT")),
                        "otra-app", Map.of("roles", List.of("ADMIN"))))
                .build();

        List<String> roles = converter.convert(jwt).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(roles).contains("ROLE_TENANT", "ROLE_OFFLINE_ACCESS").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void elPrincipalEsElSub() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("abc").issuedAt(Instant.now()).claim("x", 1).build();
        assertThat(converter.convert(jwt).getName()).isEqualTo("abc");
    }
}
