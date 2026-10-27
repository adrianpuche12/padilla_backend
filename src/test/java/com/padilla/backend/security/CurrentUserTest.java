package com.padilla.backend.security;

import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String sub, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(sub)
                .claim("email", "demo@propero.test").issuedAt(Instant.now()).build();
        List<SimpleGrantedAuthority> auths = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, auths, sub));
    }

    @Test
    void tomaElRolDeMayorJerarquiaIgnorandoRolesAjenos() {
        autenticar(UUID.randomUUID().toString(), "OFFLINE_ACCESS", "TENANT", "ADMIN");
        assertThat(CurrentUser.role()).isEqualTo(Role.ADMIN);
        assertThat(CurrentUser.isManagement()).isTrue();
    }

    @Test
    void idEsElSubDelToken() {
        UUID id = UUID.randomUUID();
        autenticar(id.toString(), "TENANT");
        assertThat(CurrentUser.id()).isEqualTo(id);
        assertThat(CurrentUser.claim("email")).contains("demo@propero.test");
    }

    @Test
    void sinSubValidoDa401EnVezDe500() {
        autenticar("no-es-uuid", "ADMIN");
        assertThatThrownBy(CurrentUser::id).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void sinRolDePropero403() {
        autenticar(UUID.randomUUID().toString(), "OFFLINE_ACCESS");
        assertThatThrownBy(CurrentUser::role).isInstanceOf(RbacException.class);
    }

    @Test
    void cascadaDeRoles() {
        assertThat(Role.SUPER_ADMIN.canManage(Role.MANAGER)).isTrue();
        assertThat(Role.ADMIN.canManage(Role.TENANT)).isTrue();
        assertThat(Role.ADMIN.canManage(Role.ADMIN)).isFalse();
        assertThat(Role.TENANT.canManage(Role.PROVIDER)).isFalse();
    }
}
