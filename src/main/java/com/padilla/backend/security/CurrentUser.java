package com.padilla.backend.security;

import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * PRO-005: unica forma de saber quien hace el request (antes estaba copiado en 6 services).
 * El id es el "sub" del JWT de Keycloak (= users.id). El rol es el de mayor jerarquia del token.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication auth = authentication();
        String name = auth.getName();
        if (name == null || name.isBlank()) {
            throw new UnauthenticatedException("El token no identifica al usuario (falta el claim sub)");
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException("El identificador del usuario en el token no es valido");
        }
    }

    public static Role role() {
        return roleOf(authentication())
                .orElseThrow(() -> new RbacException("El token no tiene ningun rol de Propero"));
    }

    public static boolean isManagement() {
        return role().isManagement();
    }

    public static boolean hasRole(Role role) {
        return role() == role;
    }

    /** Lanza 403 si quien llama no es personal de la inmobiliaria. */
    public static void requireManagement() {
        Role role = role();
        if (!role.isManagement()) {
            throw new RbacException("El rol " + role + " no puede realizar esta accion");
        }
    }

    /** Claim del JWT (email, name, preferred_username...). Vacio si no es un token JWT. */
    public static Optional<String> claim(String name) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return Optional.ofNullable(jwt.getToken().getClaimAsString(name)).filter(s -> !s.isBlank());
        }
        return Optional.empty();
    }

    static Optional<Role> roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .map(CurrentUser::parseRole)
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(Role::level));
    }

    private static Optional<Role> parseRole(String value) {
        try {
            return Optional.of(Role.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("No hay un usuario autenticado");
        }
        return auth;
    }
}
