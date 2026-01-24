package com.padilla.backend.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extraer roles de realm_access
        Collection<String> realmRoles = extractRealmRoles(jwt);

        // Extraer roles de resource_access (client roles)
        Collection<String> clientRoles = extractClientRoles(jwt);

        // Combinar todos los roles y convertirlos a GrantedAuthority
        return Stream.concat(realmRoles.stream(), clientRoles.stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object roles = realmAccess.get("roles");
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        // Buscar roles en cada cliente configurado
        return resourceAccess.values().stream()
                .filter(value -> value instanceof Map)
                .map(value -> (Map<String, Object>) value)
                .filter(clientAccess -> clientAccess.containsKey("roles"))
                .flatMap(clientAccess -> {
                    Object roles = clientAccess.get("roles");
                    if (roles instanceof List) {
                        return ((List<String>) roles).stream();
                    }
                    return Stream.empty();
                })
                .collect(Collectors.toList());
    }
}
