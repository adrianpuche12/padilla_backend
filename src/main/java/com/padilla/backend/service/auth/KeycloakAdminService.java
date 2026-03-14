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

import java.security.SecureRandom;
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

    @Value("${keycloak.client-id}")
    private String kcClientId;

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

    public String generateAndSetTemporaryPassword(String userId) {
        String password = generateSecurePassword();
        String token = getAdminToken();
        setTemporaryPassword(token, userId, password);
        return password;
    }

    public void enableUser(String email) {
        String token = getAdminToken();
        String userId = findUserIdByEmail(token, email);
        if (userId != null) {
            updateUserEnabled(token, userId, true);
            log.info("Usuario habilitado en Keycloak: {}", email);
        } else {
            log.warn("Usuario no encontrado en Keycloak para habilitar: {}", email);
        }
    }

    public String resetPasswordByEmail(String email) {
        String token = getAdminToken();
        String userId = findUserIdByEmail(token, email);
        if (userId == null) {
            throw new RuntimeException("Usuario no encontrado en Keycloak: " + email);
        }
        String password = generateSecurePassword();
        setTemporaryPassword(token, userId, password);
        log.info("Password reseteado en Keycloak para: {}", email);
        return password;
    }

    public void setPermanentPasswordById(String userId, String newPassword) {
        String token = getAdminToken();
        setTemporaryPassword(token, userId, newPassword);
        log.info("Password permanente establecido para usuario ID: {}", userId);
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

    public void deleteUserByEmail(String email) {
        String token = getAdminToken();
        // Buscamos por email para obtener el UUID real de Keycloak.
        // Esto resuelve el caso de UUIDs desincronizados (usuarios creados antes del Sprint 4).
        String userId = findUserIdByEmail(token, email);
        if (userId == null) {
            log.warn("Usuario no encontrado en Keycloak por email: {}. Solo se elimina de DB.", email);
            return;
        }
        String userUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        restTemplate.exchange(userUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        log.info("Usuario eliminado de Keycloak: {} ({})", email, userId);
    }

    /** @deprecated Usar deleteUserByEmail — este metodo falla si el UUID esta desincronizado */
    public void deleteUserById(String userId) {
        String token = getAdminToken();
        String userUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            restTemplate.exchange(userUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Usuario eliminado de Keycloak: {}", userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuario {} no encontrado en Keycloak al intentar eliminar. Se elimina solo de DB.", userId);
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
        // Los roles de Padilla estan configurados como client roles en padilla_frontend,
        // no como realm roles. Se usa la API de client roles para asignarlos.

        // 1. Obtener el ID interno (UUID) del cliente en Keycloak
        String clientInternalId = getClientInternalId(token, kcClientId);

        // 2. Obtener la representacion del client role
        String roleUrl = serverUrl + "/admin/realms/" + realm + "/clients/" + clientInternalId + "/roles/" + role.name();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> roleRepresentation = roleResponse.getBody();

        // 3. Asignar el client role al usuario
        String assignUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/clients/" + clientInternalId;
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(
                assignUrl,
                new HttpEntity<>(List.of(roleRepresentation), headers),
                Void.class
        );
        log.info("Rol '{}' asignado en Keycloak (client: {}) al usuario {}", role.name(), kcClientId, userId);
    }

    @SuppressWarnings("unchecked")
    private String getClientInternalId(String token, String clientId) {
        String clientsUrl = serverUrl + "/admin/realms/" + realm + "/clients?clientId=" + clientId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<List> response = restTemplate.exchange(
                clientsUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);

        List<Map<String, Object>> clients = response.getBody();
        if (clients == null || clients.isEmpty()) {
            throw new RuntimeException("Cliente Keycloak no encontrado: " + clientId);
        }
        return (String) clients.get(0).get("id");
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

    private void setTemporaryPassword(String token, String userId, String password) {
        String passwordUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );
        restTemplate.exchange(passwordUrl, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
