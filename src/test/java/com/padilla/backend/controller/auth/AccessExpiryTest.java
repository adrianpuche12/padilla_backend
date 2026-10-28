package com.padilla.backend.controller.auth;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.TokenResponse;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import com.padilla.backend.service.email.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** PRO-032: el acceso temporal vence a las 24 h y el primer login lo informa. */
@ExtendWith(MockitoExtension.class)
class AccessExpiryTest {

    @Mock private KeycloakAuthService keycloakAuthService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @InjectMocks private AuthController controller;

    private static LoginRequest login() {
        LoginRequest r = new LoginRequest();
        r.setUsername("nuevo@propero.test");
        r.setPassword("Temp1234!");
        return r;
    }

    private static User usuario(boolean firstLogin, OffsetDateTime vence) {
        User u = new User();
        u.setEmail("nuevo@propero.test");
        u.setRole(Role.TENANT);
        u.setFirstLogin(firstLogin);
        u.setPasswordResetExpiresAt(vence);
        return u;
    }

    @Test
    @SuppressWarnings("unchecked")
    void accesoTemporalVencidoDa403AccessExpired() {
        when(keycloakAuthService.login(any())).thenReturn(new TokenResponse());
        when(userRepository.findByEmail("nuevo@propero.test")).thenReturn(Optional.of(usuario(true, OffsetDateTime.now().minusHours(1))));
        ResponseEntity<?> r = controller.login(login());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((Map<String, String>) r.getBody()).containsEntry("error", "ACCESS_EXPIRED");
    }

    @Test
    void accesoTemporalVigenteIndicaPrimerLogin() {
        when(keycloakAuthService.login(any())).thenReturn(new TokenResponse());
        when(userRepository.findByEmail("nuevo@propero.test")).thenReturn(Optional.of(usuario(true, OffsetDateTime.now().plusHours(20))));
        ResponseEntity<?> r = controller.login(login());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((TokenResponse) r.getBody()).getFirstLogin()).isTrue();
    }
}
