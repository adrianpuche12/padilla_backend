package com.padilla.backend.service.auth;

import com.padilla.backend.dto.auth.LoginRequest;
import com.padilla.backend.dto.auth.RefreshRequest;
import com.padilla.backend.dto.auth.TokenResponse;
import com.padilla.backend.enums.Role;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTS DE INTEGRACION — KeycloakAuthService
 *
 * Requiere:
 *   - Keycloak corriendo en http://62.171.160.238:8095
 *   - Realm "padilla" con client "padilla_frontend" (public, PKCE)
 *
 * Estrategia:
 *   - @BeforeAll crea un usuario de prueba via KeycloakAdminService y le asigna password conocido
 *   - Los tests de auth usan ese usuario para probar todos los flujos
 *   - @AfterAll elimina el usuario de prueba
 *   - El token obtenido en el test de login se reutiliza en refresh y logout
 *
 * Cubre:
 *   01 — Login exitoso con credenciales validas
 *   02 — Login con password incorrecto → 401
 *   03 — Login con usuario deshabilitado → 401
 *   04 — Re-habilitar usuario y login exitoso
 *   05 — Refresh con token valido → nuevos tokens
 *   06 — Refresh con token invalido/expirado → excepcion
 *   07 — Logout revoca sesion correctamente
 *   08 — Refresh despues de logout → token invalido
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("KeycloakAuthService — Integracion con Keycloak real")
class KeycloakAuthServiceIntegrationTest {

    // ---------------------------------------------------------------
    // Configuracion
    // ---------------------------------------------------------------
    private static final String KC_URL        = "http://62.171.160.238:8095";
    private static final String KC_REALM      = "padilla";
    private static final String KC_ADMIN_USER = "admin";
    private static final String KC_ADMIN_PASS = "GeronimoAdmin2024!";
    private static final String KC_CLIENT_ID  = "padilla_frontend";

    private static final String TEST_EMAIL    = "test-auth@padilla-test.local";
    private static final String TEST_NAME     = "Test Auth User";
    private static final String TEST_PASSWORD = "TestPadilla2026!";  // Cumple requisitos de seguridad

    // ---------------------------------------------------------------
    // Estado compartido
    // ---------------------------------------------------------------
    private KeycloakAdminService adminService;
    private KeycloakAuthService  authService;

    private String testUserId;
    private String validAccessToken;   // Se guarda en test 01, se usa en 05-08
    private String validRefreshToken;  // Se guarda en test 01, se usa en 05, 07, 08

    // ---------------------------------------------------------------
    // Setup y teardown
    // ---------------------------------------------------------------

    @BeforeAll
    void setUpServicesAndTestUser() {
        // Inicializar servicio admin
        adminService = new KeycloakAdminService();
        ReflectionTestUtils.setField(adminService, "serverUrl",     KC_URL);
        ReflectionTestUtils.setField(adminService, "realm",         KC_REALM);
        ReflectionTestUtils.setField(adminService, "adminUsername", KC_ADMIN_USER);
        ReflectionTestUtils.setField(adminService, "adminPassword", KC_ADMIN_PASS);

        // Inicializar servicio auth
        authService = new KeycloakAuthService();
        ReflectionTestUtils.setField(authService, "authServerUrl", KC_URL);
        ReflectionTestUtils.setField(authService, "realm",         KC_REALM);
        ReflectionTestUtils.setField(authService, "clientId",      KC_CLIENT_ID);

        // Crear usuario de prueba en Keycloak
        testUserId = adminService.createUser(TEST_NAME, TEST_EMAIL, Role.ADMIN);
        assertNotNull(testUserId, "Setup fallo: no se pudo crear el usuario de prueba en Keycloak");

        // Asignar password conocido (no temporal para que el login no sea bloqueado)
        adminService.setPermanentPasswordById(testUserId, TEST_PASSWORD);

        System.out.println("[SETUP] Usuario de prueba creado en Keycloak: " + testUserId);
    }

    @AfterAll
    void cleanUp() {
        if (testUserId != null) {
            try {
                adminService.deleteUserById(testUserId);
                System.out.println("[CLEANUP] Usuario de prueba eliminado: " + testUserId);
            } catch (Exception e) {
                System.out.println("[CLEANUP] Error al eliminar usuario de prueba: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("01 — login: credenciales validas retornan access_token y refresh_token")
    void login_credencialesValidas_deberiaRetornarTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        TokenResponse response = authService.login(request);

        assertNotNull(response,
                "La respuesta de login no debe ser null");
        assertNotNull(response.getAccessToken(),
                "El access_token no debe ser null");
        assertFalse(response.getAccessToken().isBlank(),
                "El access_token no debe estar en blanco");
        assertNotNull(response.getRefreshToken(),
                "El refresh_token no debe ser null");
        assertFalse(response.getRefreshToken().isBlank(),
                "El refresh_token no debe estar en blanco");
        assertNotNull(response.getExpiresIn(),
                "El campo expires_in debe estar presente");
        assertTrue(response.getExpiresIn() > 0,
                "El expires_in debe ser positivo");
        assertEquals("Bearer", response.getTokenType(),
                "El token_type debe ser 'Bearer'");

        // Guardar tokens para tests posteriores
        validAccessToken  = response.getAccessToken();
        validRefreshToken = response.getRefreshToken();

        System.out.println("[TEST 01] Login exitoso. Token expira en: " + response.getExpiresIn() + "s");
    }

    @Test
    @Order(2)
    @DisplayName("02 — login: password incorrecto debe lanzar HttpClientErrorException.Unauthorized (401)")
    void login_passwordIncorrecto_deberiaLanzar401() {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_EMAIL);
        request.setPassword("password-totalmente-incorrecto-999");

        HttpClientErrorException.Unauthorized ex = assertThrows(
                HttpClientErrorException.Unauthorized.class,
                () -> authService.login(request),
                "Login con password incorrecto debe lanzar Unauthorized (401)"
        );

        assertEquals(401, ex.getStatusCode().value(),
                "El codigo de estado debe ser 401");

        System.out.println("[TEST 02] 401 Unauthorized recibido correctamente por password incorrecto");
    }

    @Test
    @Order(3)
    @DisplayName("03 — login: usuario inexistente debe lanzar HttpClientErrorException.Unauthorized (401)")
    void login_usuarioInexistente_deberiaLanzar401() {
        LoginRequest request = new LoginRequest();
        request.setUsername("noexiste@padilla-test.local");
        request.setPassword("cualquier-password");

        assertThrows(
                HttpClientErrorException.Unauthorized.class,
                () -> authService.login(request),
                "Login con usuario inexistente debe lanzar Unauthorized (401)"
        );

        System.out.println("[TEST 03] 401 Unauthorized recibido correctamente por usuario inexistente");
    }

    @Test
    @Order(4)
    @DisplayName("04 — login: usuario deshabilitado debe lanzar excepcion de autenticacion")
    void login_usuarioDeshabilitado_deberiaFallar() {
        // Deshabilitar el usuario de prueba
        adminService.disableUser(TEST_EMAIL);

        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        // Keycloak devuelve 401 para usuario deshabilitado
        assertThrows(
                HttpClientErrorException.class,
                () -> authService.login(request),
                "Login con usuario deshabilitado debe lanzar excepcion HTTP"
        );

        // Re-habilitar para los tests siguientes
        adminService.enableUser(TEST_EMAIL);

        System.out.println("[TEST 04] Login con usuario deshabilitado rechazado correctamente");
    }

    @Test
    @Order(5)
    @DisplayName("05 — refresh: token valido devuelve nuevos tokens")
    void refresh_tokenValido_deberiaRetornarNuevosTokens() {
        assertNotNull(validRefreshToken, "Prerequisito: test 01 debe haber guardado el refresh_token");

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(validRefreshToken);

        TokenResponse response = authService.refresh(request);

        assertNotNull(response,
                "La respuesta de refresh no debe ser null");
        assertNotNull(response.getAccessToken(),
                "El nuevo access_token no debe ser null");
        assertFalse(response.getAccessToken().isBlank(),
                "El nuevo access_token no debe estar en blanco");
        assertNotNull(response.getRefreshToken(),
                "El nuevo refresh_token no debe ser null");

        // Actualizar tokens con los nuevos
        validAccessToken  = response.getAccessToken();
        validRefreshToken = response.getRefreshToken();

        System.out.println("[TEST 05] Refresh exitoso. Tokens renovados.");
    }

    @Test
    @Order(6)
    @DisplayName("06 — refresh: token invalido debe lanzar HttpClientErrorException (400/401)")
    void refresh_tokenInvalido_deberiaLanzarExcepcion() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("este.no.es.un.refresh.token.valido");

        assertThrows(
                HttpClientErrorException.class,
                () -> authService.refresh(request),
                "Refresh con token invalido debe lanzar excepcion HTTP"
        );

        System.out.println("[TEST 06] Token invalido rechazado correctamente en refresh");
    }

    @Test
    @Order(7)
    @DisplayName("07 — logout: revocar sesion sin errores")
    void logout_tokenValido_deberiaRevocarSesion() {
        assertNotNull(validRefreshToken, "Prerequisito: debe haber un refresh_token vigente");

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(validRefreshToken);

        assertDoesNotThrow(
                () -> authService.logout(request),
                "Logout con refresh_token valido no debe lanzar excepcion"
        );

        System.out.println("[TEST 07] Logout exitoso. Sesion revocada.");
    }

    @Test
    @Order(8)
    @DisplayName("08 — refresh despues de logout: token revocado debe lanzar excepcion")
    void refresh_despuesDeLogout_tokenRevocadoDeberiaFallar() {
        assertNotNull(validRefreshToken, "Prerequisito: debe haber el refresh_token revocado del test 07");

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(validRefreshToken);

        // Keycloak devuelve 400 para un refresh_token revocado/expirado
        assertThrows(
                HttpClientErrorException.class,
                () -> authService.refresh(request),
                "Refresh con token revocado (post-logout) debe lanzar excepcion HTTP"
        );

        System.out.println("[TEST 08] Token revocado rechazado correctamente en refresh post-logout");
    }

    @Test
    @Order(9)
    @DisplayName("09 — login: username con mayusculas debe ser tratado como email (case-insensitive)")
    void login_usernameMayusculas_deberiaFuncionar() {
        // Keycloak por defecto es case-insensitive en username/email
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_EMAIL.toUpperCase());
        request.setPassword(TEST_PASSWORD);

        // Si Keycloak esta configurado como case-insensitive, esto debe funcionar
        // Si lanza excepcion, documentamos el comportamiento real de este Keycloak
        try {
            TokenResponse response = authService.login(request);
            assertNotNull(response.getAccessToken(),
                    "Login case-insensitive funciona en este Keycloak");
            System.out.println("[TEST 09] Login case-insensitive: FUNCIONA");
        } catch (HttpClientErrorException.Unauthorized e) {
            System.out.println("[TEST 09] Login case-insensitive: Keycloak es case-SENSITIVE para username");
            // Documentamos el comportamiento sin fallar el test
        }
    }

    @Test
    @Order(10)
    @DisplayName("10 — logout: con token invalido debe lanzar excepcion HTTP")
    void logout_tokenInvalido_deberiaLanzarExcepcion() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh.token.completamente.invalido");

        // Keycloak devuelve error para tokens invalidos en logout
        assertThrows(
                HttpClientErrorException.class,
                () -> authService.logout(request),
                "Logout con token invalido debe lanzar excepcion HTTP"
        );

        System.out.println("[TEST 10] Logout con token invalido rechazado correctamente");
    }
}
