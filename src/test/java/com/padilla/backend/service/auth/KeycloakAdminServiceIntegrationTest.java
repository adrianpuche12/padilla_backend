package com.padilla.backend.service.auth;

import com.padilla.backend.enums.Role;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTS DE INTEGRACION — KeycloakAdminService
 *
 * Requiere:
 *   - Keycloak corriendo en http://62.171.160.238:8095
 *   - Client "padilla_frontend" con client roles: SUPER_ADMIN, MANAGER, ADMIN, OWNER, TENANT, PROVIDER
 *   - Admin "admin" / "GeronimoAdmin2024!" con permisos sobre el realm
 *
 * Estrategia de aislamiento:
 *   - Se crea un usuario de prueba unico al inicio (@BeforeAll)
 *   - Todos los tests operan sobre ese usuario
 *   - El @AfterAll garantiza la limpieza aunque fallen tests intermedios
 *   - El usuario de prueba usa email: test-auto@padilla-test.local (claramente no-productivo)
 *
 * Orden de ejecucion:
 *   01 → Crear usuario y obtener UUID de Keycloak
 *   02 → Generar password temporal
 *   03 → Deshabilitar usuario
 *   04 → Habilitar usuario
 *   05 → Resetear password por email
 *   06 → Establecer password permanente por ID
 *   07 → Email duplicado lanza excepcion
 *   08 → Eliminar usuario permanentemente y verificar que ya no existe
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("KeycloakAdminService — Integracion con Keycloak real")
class KeycloakAdminServiceIntegrationTest {

    // ---------------------------------------------------------------
    // Configuracion del servidor Keycloak
    // ---------------------------------------------------------------
    private static final String KC_URL        = "http://62.171.160.238:8095";
    private static final String KC_REALM      = "padilla";
    private static final String KC_ADMIN_USER = "admin";
    private static final String KC_ADMIN_PASS = "GeronimoAdmin2024!";

    // Usuario de prueba — NO es un usuario real del sistema
    private static final String TEST_EMAIL = "test-auto@padilla-test.local";
    private static final String TEST_NAME  = "Test Automation";

    // ---------------------------------------------------------------
    // Estado compartido entre tests (cycle de vida PER_CLASS)
    // ---------------------------------------------------------------
    private KeycloakAdminService adminService;
    private String createdUserId;    // UUID asignado por Keycloak al usuario de prueba
    private String currentPassword;  // Password vigente en cada momento del ciclo

    // ---------------------------------------------------------------
    // Setup y teardown
    // ---------------------------------------------------------------

    @BeforeAll
    void setUpService() {
        adminService = new KeycloakAdminService();
        ReflectionTestUtils.setField(adminService, "serverUrl",     KC_URL);
        ReflectionTestUtils.setField(adminService, "realm",         KC_REALM);
        ReflectionTestUtils.setField(adminService, "adminUsername", KC_ADMIN_USER);
        ReflectionTestUtils.setField(adminService, "adminPassword", KC_ADMIN_PASS);
        ReflectionTestUtils.setField(adminService, "kcClientId",    "padilla_frontend");
    }

    @AfterAll
    void cleanUp() {
        // Garantizar limpieza aunque el test de delete haya fallado
        if (createdUserId != null) {
            try {
                adminService.deleteUserById(createdUserId);
                System.out.println("[CLEANUP] Usuario de prueba eliminado: " + createdUserId);
            } catch (Exception e) {
                System.out.println("[CLEANUP] Usuario ya fue eliminado o no encontrado — OK");
            }
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("01 — createUser: crear usuario en Keycloak y obtener UUID valido")
    void createUser_deberiaRetornarUUIDValido() {
        String userId = adminService.createUser(TEST_NAME, TEST_EMAIL, Role.ADMIN);

        assertNotNull(userId,
                "Keycloak debe devolver el UUID del usuario creado");
        assertDoesNotThrow(() -> UUID.fromString(userId),
                "El ID devuelto por Keycloak debe ser un UUID valido");

        createdUserId = userId;
        System.out.println("[TEST 01] Usuario creado con UUID: " + createdUserId);
    }

    @Test
    @Order(2)
    @DisplayName("02 — generateAndSetTemporaryPassword: generar password temporal de 12 caracteres")
    void generateAndSetTemporaryPassword_deberiaRetornarPasswordValido() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        String password = adminService.generateAndSetTemporaryPassword(createdUserId);

        assertNotNull(password,
                "El password temporal generado no debe ser null");
        assertEquals(12, password.length(),
                "El password temporal debe tener exactamente 12 caracteres");
        assertFalse(password.isBlank(),
                "El password temporal no debe estar en blanco");

        currentPassword = password;
        System.out.println("[TEST 02] Password temporal generado exitosamente (longitud: " + password.length() + ")");
    }

    @Test
    @Order(3)
    @DisplayName("03 — disableUser: deshabilitar usuario por email sin errores")
    void disableUser_deberiaDeshabilitarUsuarioSinExcepcion() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        assertDoesNotThrow(
                () -> adminService.disableUser(TEST_EMAIL),
                "Deshabilitar un usuario existente no debe lanzar excepcion"
        );

        System.out.println("[TEST 03] Usuario deshabilitado: " + TEST_EMAIL);
    }

    @Test
    @Order(4)
    @DisplayName("04 — enableUser: habilitar usuario por email sin errores")
    void enableUser_deberiaHabilitarUsuarioSinExcepcion() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        assertDoesNotThrow(
                () -> adminService.enableUser(TEST_EMAIL),
                "Habilitar un usuario existente no debe lanzar excepcion"
        );

        System.out.println("[TEST 04] Usuario habilitado: " + TEST_EMAIL);
    }

    @Test
    @Order(5)
    @DisplayName("05 — resetPasswordByEmail: resetear password y recibir nuevo password de 12 chars")
    void resetPasswordByEmail_deberiaRetornarNuevoPassword() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        String newPassword = adminService.resetPasswordByEmail(TEST_EMAIL);

        assertNotNull(newPassword,
                "El nuevo password no debe ser null");
        assertEquals(12, newPassword.length(),
                "El nuevo password debe tener 12 caracteres");
        assertFalse(newPassword.isBlank(),
                "El nuevo password no debe estar en blanco");

        currentPassword = newPassword;
        System.out.println("[TEST 05] Password reseteado exitosamente para: " + TEST_EMAIL);
    }

    @Test
    @Order(6)
    @DisplayName("06 — setPermanentPasswordById: establecer password permanente por UUID")
    void setPermanentPasswordById_deberiaEstablecerPasswordSinExcepcion() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        String permanentPassword = "Padilla2026!Test";

        assertDoesNotThrow(
                () -> adminService.setPermanentPasswordById(createdUserId, permanentPassword),
                "Establecer password permanente no debe lanzar excepcion"
        );

        currentPassword = permanentPassword;
        System.out.println("[TEST 06] Password permanente establecido para UUID: " + createdUserId);
    }

    @Test
    @Order(7)
    @DisplayName("07 — createUser duplicado: email existente debe lanzar IllegalStateException")
    void createUser_emailDuplicado_deberiaLanzarIllegalStateException() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> adminService.createUser(TEST_NAME, TEST_EMAIL, Role.ADMIN),
                "Crear un usuario con email ya registrado debe lanzar IllegalStateException"
        );

        assertTrue(ex.getMessage().contains("ya está registrado"),
                "El mensaje de error debe indicar que el email ya existe");

        System.out.println("[TEST 07] Duplicado rechazado correctamente: " + ex.getMessage());
    }

    @Test
    @Order(8)
    @DisplayName("08 — deleteUserById: eliminar usuario de Keycloak y verificar que ya no existe")
    void deleteUserById_deberiaEliminarUsuarioYVerificarAusencia() {
        assertNotNull(createdUserId, "Prerequisito: test 01 debe haber creado el usuario");

        String idToDelete = createdUserId;

        // Eliminar usuario
        assertDoesNotThrow(
                () -> adminService.deleteUserById(idToDelete),
                "Eliminar un usuario existente no debe lanzar excepcion"
        );

        // Marcar como eliminado para que @AfterAll no intente eliminarlo de nuevo
        createdUserId = null;

        // Verificar que ya no existe: resetPasswordByEmail lanza RuntimeException si no lo encuentra
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> adminService.resetPasswordByEmail(TEST_EMAIL),
                "Despues de eliminar el usuario, cualquier operacion por email debe fallar"
        );

        assertTrue(ex.getMessage().contains("no encontrado"),
                "El error debe indicar que el usuario no fue encontrado en Keycloak");

        System.out.println("[TEST 08] Usuario eliminado y ausencia verificada: " + idToDelete);
    }

    @Test
    @Order(9)
    @DisplayName("09 — disableUser con email inexistente: debe advertir sin lanzar excepcion")
    void disableUser_emailInexistente_noDeberiaLanzarExcepcion() {
        // disableUser usa findUserIdByEmail que retorna null si no existe
        // el metodo logea un warning pero NO lanza excepcion
        assertDoesNotThrow(
                () -> adminService.disableUser("usuario-que-no-existe@padilla-test.local"),
                "disableUser con email inexistente debe manejar el caso sin lanzar excepcion"
        );

        System.out.println("[TEST 09] disableUser con email inexistente manejado correctamente");
    }

    @Test
    @Order(10)
    @DisplayName("10 — enableUser con email inexistente: debe advertir sin lanzar excepcion")
    void enableUser_emailInexistente_noDeberiaLanzarExcepcion() {
        assertDoesNotThrow(
                () -> adminService.enableUser("usuario-que-no-existe@padilla-test.local"),
                "enableUser con email inexistente debe manejar el caso sin lanzar excepcion"
        );

        System.out.println("[TEST 10] enableUser con email inexistente manejado correctamente");
    }

    @Test
    @Order(11)
    @DisplayName("11 — resetPasswordByEmail con email inexistente: debe lanzar RuntimeException")
    void resetPasswordByEmail_emailInexistente_deberiaLanzarRuntimeException() {
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> adminService.resetPasswordByEmail("no-existe@padilla-test.local"),
                "resetPasswordByEmail con email inexistente debe lanzar RuntimeException"
        );

        assertNotNull(ex.getMessage(), "La excepcion debe tener un mensaje descriptivo");
        System.out.println("[TEST 11] RuntimeException correcta: " + ex.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("12 — deleteUserById con UUID inexistente: no debe lanzar excepcion (huerfano de DB)")
    void deleteUserById_uuidInexistenteEnKeycloak_noDeberiaLanzarExcepcion() {
        // Simula el caso de un usuario con UUID desincronizado (creado antes del Sprint 3):
        // existe en DB pero no en Keycloak. El delete debe completarse sin error.
        String uuidFantasma = "00000000-0000-0000-0000-000000000000";

        assertDoesNotThrow(
                () -> adminService.deleteUserById(uuidFantasma),
                "deleteUserById con UUID inexistente en Keycloak debe manejar el 404 sin lanzar excepcion"
        );

        System.out.println("[TEST 12] UUID inexistente en Keycloak manejado correctamente (huerfano de DB)");
    }

    @Test
    @Order(14)
    @DisplayName("14 — Credenciales admin invalidas: getAdminToken debe lanzar excepcion")
    void getAdminToken_credencialesInvalidas_deberiaFallar() {
        KeycloakAdminService serviceMalasCreds = new KeycloakAdminService();
        ReflectionTestUtils.setField(serviceMalasCreds, "serverUrl",     KC_URL);
        ReflectionTestUtils.setField(serviceMalasCreds, "realm",         KC_REALM);
        ReflectionTestUtils.setField(serviceMalasCreds, "adminUsername", "admin");
        ReflectionTestUtils.setField(serviceMalasCreds, "adminPassword", "password-incorrecto-9999");

        // Cualquier operacion que requiera token admin debe fallar
        assertThrows(
                Exception.class,
                () -> serviceMalasCreds.createUser("Test", "test@padilla.test", Role.ADMIN),
                "Con credenciales admin incorrectas, toda operacion debe lanzar excepcion"
        );

        System.out.println("[TEST 12] Credenciales admin invalidas rechazadas correctamente");
    }
}
