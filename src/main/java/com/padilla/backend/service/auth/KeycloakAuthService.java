package com.padilla.backend.service.auth;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.RefreshRequest;
import com.padilla.backend.dto.auth.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class KeycloakAuthService {

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    public KeycloakAuthService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Autentica un usuario contra Keycloak y devuelve los tokens
     */
    public TokenResponse login(LoginRequest loginRequest) {
        String tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("username", loginRequest.getUsername());
        body.add("password", loginRequest.getPassword());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    TokenResponse.class
            );
            log.info("Login exitoso para usuario: {}", loginRequest.getUsername());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Error en login para usuario {}: {}", loginRequest.getUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * Renueva los tokens usando el refresh_token
     */
    public TokenResponse refresh(RefreshRequest refreshRequest) {
        String tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshRequest.getRefreshToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    TokenResponse.class
            );
            log.info("Token renovado exitosamente");
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Error al renovar token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Cierra la sesion revocando el refresh_token en Keycloak
     */
    public void logout(RefreshRequest refreshRequest) {
        String logoutUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("refresh_token", refreshRequest.getRefreshToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(logoutUrl, request, Void.class);
            log.info("Logout exitoso");
        } catch (HttpClientErrorException e) {
            log.error("Error en logout: {}", e.getMessage());
            throw e;
        }
    }
}
