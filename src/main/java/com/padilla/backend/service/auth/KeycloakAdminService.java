package com.padilla.backend.service.auth;

import com.padilla.backend.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeycloakAdminService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    // ---------------------------------------------------
    // API publica
    // ---------------------------------------------------

    public String createUser(String name, String email, Role role) {
        String token = getAdminToken();
        String userId = createKeycloakUser(token, name, email);
        assignRole(token, userId, role);
        log.info("Usuario creado en Keycloak: {} con rol {}", email, role);
        return userId;
    }

    public void disableUser(String email) {
        String token = getAdminToken();
        String userId = findUserIdByEmail(token, email);
        if (userId != null) {
            updateUserEnabled(token, userId, false);
            log.info("Usuario deshabilitado en Keycloak: {}", email);
        } else {
            log.warn("Usuario no encontrado en Keycloak para deshabilitar: {}", email);
        }
    }

    // ---------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------

    private String getAdminToken() {
        String tokenUrl = serverUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        return (String) response.getBody().get("access_token");
    }

    private String createKeycloakUser(String token, String name, String email) {
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> userBody = Map.of(
                "username", email,
                "email", email,
                "firstName", name,
                "enabled", true,
                "emailVerified", true
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userBody, headers);
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(usersUrl, request, Void.class);
            // El ID del usuario creado viene en el header Location
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            return location != null ? location.substring(location.lastIndexOf('/') + 1) : null;
        } catch (HttpClientErrorException.Conflict e) {
            throw new IllegalStateException("El email ya está registrado en el sistema: " + email);
        }
    }

    private void assignRole(String token, String userId, Role role) {
        try {
            // 1. Obtener la representacion del rol en Keycloak
            String roleUrl = serverUrl + "/admin/realms/" + realm + "/roles/" + role.name();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> roleRepresentation = roleResponse.getBody();

            // 2. Asignar el rol al usuario
            String assignUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(
                    assignUrl,
                    new HttpEntity<>(List.of(roleRepresentation), headers),
                    Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Rol '{}' no encontrado en Keycloak, se omite asignacion de rol para usuario {}", role.name(), userId);
        }
    }

    @SuppressWarnings("unchecked")
    private String findUserIdByEmail(String token, String email) {
        String searchUrl = serverUrl + "/admin/realms/" + realm + "/users?email=" + email + "&exact=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<List> response = restTemplate.exchange(
                searchUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);

        List<Map<String, Object>> users = response.getBody();
        if (users != null && !users.isEmpty()) {
            return (String) users.get(0).get("id");
        }
        return null;
    }

    private void updateUserEnabled(String token, String userId, boolean enabled) {
        String userUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of("enabled", enabled);
        restTemplate.exchange(userUrl, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }
}
