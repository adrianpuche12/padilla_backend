package com.padilla.backend.controller.auth;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.RefreshRequest;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import com.padilla.backend.service.email.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * PRO-010: los errores de Keycloak ya no se reenvian tal cual al navegador.
 * Un 401 del API significa siempre "credenciales invalidas" o "sesion vencida", nunca un problema de Keycloak.
 */
@ExtendWith(MockitoExtension.class)
class AuthErrorMappingTest {

    @Mock private KeycloakAuthService keycloakAuthService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @InjectMocks private AuthController controller;

    private static LoginRequest login() {
        LoginRequest r = new LoginRequest();
        r.setUsername("demo@propero.test");
        r.setPassword("x");
        return r;
    }

    private static RefreshRequest refresh() {
        RefreshRequest r = new RefreshRequest();
        r.setRefreshToken("rt");
        return r;
    }

    private static HttpClientErrorException kc(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginConCredencialesInvalidasDa401ConMensaje() {
        when(keycloakAuthService.login(any())).thenThrow(kc(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_grant\"}"));
        ResponseEntity<?> r = controller.login(login());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<String, String>) r.getBody())).containsEntry("error", "INVALID_CREDENTIALS");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginDeCuentaDesactivadaMantieneElContratoConElFrontend() {
        when(keycloakAuthService.login(any())).thenThrow(kc(HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Account disabled\"}"));
        ResponseEntity<?> r = controller.login(login());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<String, String>) r.getBody()).get("detail")).contains("Account disabled");
    }

    @Test
    void keycloakCaidoEnLoginDa502YNo401() {
        when(keycloakAuthService.login(any())).thenThrow(new ResourceAccessException("Connection refused"));
        assertThat(controller.login(login()).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void errorDeConfiguracionDeKeycloakDa502() {
        when(keycloakAuthService.login(any())).thenThrow(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "x", HttpHeaders.EMPTY, new byte[0], null));
        assertThat(controller.login(login()).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshVencidoDa401SessionExpired() {
        when(keycloakAuthService.refresh(any())).thenThrow(kc(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\"}"));
        ResponseEntity<?> r = controller.refresh(refresh());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<String, String>) r.getBody())).containsEntry("error", "SESSION_EXPIRED");
    }

    @Test
    void logoutConTokenYaInvalidoIgualDa200() {
        doThrow(kc(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\"}")).when(keycloakAuthService).logout(any());
        assertThat(controller.logout(refresh()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
