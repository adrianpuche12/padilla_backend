package com.padilla.backend.controller.auth;

import com.padilla.backend.dto.auth.ForgotPasswordRequest;
import com.padilla.backend.entity.User;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import com.padilla.backend.service.email.EmailService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TESTS UNITARIOS — AuthController.forgotPassword()
 *
 * No requiere Keycloak ni DB. Todo externo está mockeado con Mockito.
 *
 * Cubre:
 *   Seguridad   — El endpoint NUNCA revela si el email existe, si la cuenta está
 *                 inactiva, ni si hubo un error interno (siempre 200)
 *   Flujo OK    — Keycloak reseteado, DB actualizada (firstLogin=true,
 *                 passwordResetExpiresAt=+24h), email enviado
 *   Normalización — El email se convierte a minúsculas y se hace trim antes de
 *                   cualquier operación
 *
 * Orden de ejecución:
 *   01 → Email no registrado: 200 sin tocar Keycloak
 *   02 → Usuario inactivo: 200 sin tocar Keycloak
 *   03 → Error en Keycloak: 200 sin exponer el error, sin enviar email
 *   04 → Flujo completo: Keycloak + DB + email
 *   05 → passwordResetExpiresAt establecido a ~24 horas en el futuro
 *   06 → Email con mayúsculas normalizado a minúsculas
 *   07 → Email con espacios trimmeado
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AuthController — Flujo olvidé mi contraseña")
class ForgotPasswordTest {

    @Mock private KeycloakAuthService  keycloakAuthService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private UserRepository       userRepository;
    @Mock private EmailService         emailService;

    @InjectMocks
    private AuthController authController;

    private static final String TEST_EMAIL    = "test@padilla.test";
    private static final String TEMP_PASSWORD = "TempPass123!";
    private static final String MSG_GENERICO  =
            "Si el email está registrado, recibirás las instrucciones en tu correo.";

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ForgotPasswordRequest request(String email) {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        return req;
    }

    private User usuarioActivo() {
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setName("Test User");
        user.setActive(true);
        user.setFirstLogin(false);
        return user;
    }

    // ---------------------------------------------------------------
    // Sección 1 — Seguridad: nunca revelar información del sistema
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Seguridad — respuesta siempre 200 y mensaje genérico")
    class SeguridadTests {

        @Test
        @Order(1)
        @DisplayName("01 — email no registrado: retorna 200 sin llamar a Keycloak ni enviar email")
        void emailNoRegistrado_retorna200SinLlamarKeycloak() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            ResponseEntity<?> response = authController.forgotPassword(request(TEST_EMAIL));

            assertEquals(200, response.getStatusCode().value());
            assertMensajeGenerico(response);
            verifyNoInteractions(keycloakAdminService);
            verifyNoInteractions(emailService);
        }

        @Test
        @Order(2)
        @DisplayName("02 — usuario inactivo: retorna 200 con mismo mensaje genérico sin tocar Keycloak")
        void usuarioInactivo_retorna200ConMensajeGenerico() {
            User inactivo = usuarioActivo();
            inactivo.setActive(false);
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(inactivo));

            ResponseEntity<?> response = authController.forgotPassword(request(TEST_EMAIL));

            assertEquals(200, response.getStatusCode().value());
            assertMensajeGenerico(response);
            verifyNoInteractions(keycloakAdminService);
            verifyNoInteractions(emailService);
        }

        @Test
        @Order(3)
        @DisplayName("03 — error en Keycloak: retorna 200 sin exponer el error, sin enviar email")
        void errorEnKeycloak_retorna200SinExponerErrorYSinEmail() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(usuarioActivo()));
            when(keycloakAdminService.resetPasswordByEmail(TEST_EMAIL))
                    .thenThrow(new RuntimeException("Keycloak no disponible"));

            ResponseEntity<?> response = authController.forgotPassword(request(TEST_EMAIL));

            assertEquals(200, response.getStatusCode().value());
            // Si Keycloak falla, la excepción es capturada — el email NO se intenta enviar
            verifyNoInteractions(emailService);
        }
    }

    // ---------------------------------------------------------------
    // Sección 2 — Flujo exitoso
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Flujo exitoso — Keycloak + DB + email")
    class FlujoExitosoTests {

        @Test
        @Order(4)
        @DisplayName("04 — flujo completo: resetea Keycloak, activa firstLogin en DB y envía email")
        void flujoCompleto_resetPasswordActualizaDbYEnviaEmail() {
            User user = usuarioActivo();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail(TEST_EMAIL)).thenReturn(TEMP_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(user);

            ResponseEntity<?> response = authController.forgotPassword(request(TEST_EMAIL));

            assertEquals(200, response.getStatusCode().value());

            // Keycloak recibe el reset
            verify(keycloakAdminService).resetPasswordByEmail(TEST_EMAIL);

            // DB actualizada: firstLogin=true y passwordResetExpiresAt seteado
            verify(userRepository).save(argThat(u ->
                    u.isFirstLogin() && u.getPasswordResetExpiresAt() != null
            ));

            // Email enviado con los datos correctos
            verify(emailService).sendPasswordResetEmail(TEST_EMAIL, "Test User", TEMP_PASSWORD);
        }

        @Test
        @Order(5)
        @DisplayName("05 — passwordResetExpiresAt se establece aproximadamente 24 horas en el futuro")
        void flujoCompleto_passwordResetExpiresAtEn24Horas() {
            User user = usuarioActivo();
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail(TEST_EMAIL)).thenReturn(TEMP_PASSWORD);
            when(userRepository.save(any(User.class))).thenReturn(user);

            OffsetDateTime testStart = OffsetDateTime.now();
            authController.forgotPassword(request(TEST_EMAIL));

            // El expiry debe estar dentro del rango [testStart+23h, testStart+25h]
            verify(userRepository).save(argThat(u -> {
                OffsetDateTime expiry = u.getPasswordResetExpiresAt();
                return expiry != null
                        && expiry.isAfter(testStart.plusHours(23))
                        && expiry.isBefore(testStart.plusHours(25));
            }));
        }
    }

    // ---------------------------------------------------------------
    // Sección 3 — Normalización del email
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Normalización — email siempre en minúsculas y sin espacios")
    class NormalizacionTests {

        @Test
        @Order(6)
        @DisplayName("06 — email con mayúsculas: se convierte a minúsculas antes de buscar en DB")
        void emailConMayusculas_seNormalizaAMinusculas() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            authController.forgotPassword(request("TEST@PADILLA.TEST"));

            verify(userRepository).findByEmail("test@padilla.test");
        }

        @Test
        @Order(7)
        @DisplayName("07 — email con espacios: se hace trim antes de buscar en DB")
        void emailConEspacios_seTrimea() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            authController.forgotPassword(request("  test@padilla.test  "));

            verify(userRepository).findByEmail("test@padilla.test");
        }
    }

    // ---------------------------------------------------------------
    // Helper de aserción
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void assertMensajeGenerico(ResponseEntity<?> response) {
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body, "El body de la respuesta no debe ser null");
        assertEquals(MSG_GENERICO, body.get("message"),
                "El mensaje debe ser siempre el genérico para no revelar información");
    }
}
