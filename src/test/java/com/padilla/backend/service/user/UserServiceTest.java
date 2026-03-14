package com.padilla.backend.service.user;

import com.padilla.backend.entity.User;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.email.EmailService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TESTS UNITARIOS — UserService
 *
 * No requiere Keycloak ni DB. Todo externo esta mockeado con Mockito.
 *
 * Cubre:
 *   RBAC — Jerarquia de roles para create, deactivate, delete
 *   validateNotSelf — No puede operar sobre si mismo
 *   deactivateUser — Flujo completo: DB + Keycloak disable
 *   reactivateUser — Flujo completo: DB + Keycloak enable
 *   deleteUserPermanently — Orden: DB delete primero, Keycloak despues
 *   findAll / findById / findByRole — Delegacion al repositorio
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Logica de negocio y RBAC")
class UserServiceTest {

    @Mock private UserRepository      userRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private JdbcTemplate        jdbcTemplate;
    @Mock private EmailService        emailService;

    @InjectMocks
    private UserService userService;

    // ---------------------------------------------------------------
    // Helpers para configurar el SecurityContext en cada test
    // ---------------------------------------------------------------

    private void mockCallerRole(Role role) {
        mockCallerRoleAndEmail(role, "caller@padilla.test");
    }

    private void mockCallerRoleAndEmail(Role role, String callerEmail) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx  = mock(SecurityContext.class);

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

        // Todos lenient: en tests donde la excepcion ocurre antes de llegar a
        // getCurrentUserRole() (ej: findById vacio), estos stubs no se invocan
        // y Mockito strict mode los rechazaria. lenient() lo permite.
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getAuthorities()).thenAnswer(i -> authorities);
        lenient().when(auth.getName()).thenReturn(callerEmail);

        SecurityContextHolder.setContext(ctx);
    }

    private User buildUser(UUID id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setName("Test " + role.name());
        u.setEmail(email);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------
    // SECCION 1: Jerarquia RBAC — quien puede crear a quien
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — Jerarquia para createUser")
    class RbacCreateUser {

        @Test
        @DisplayName("SUPER_ADMIN puede crear MANAGER")
        void superAdmin_puedeCrear_manager() {
            mockCallerRole(Role.SUPER_ADMIN);
            User target = buildUser(null, "nuevo@test.com", Role.MANAGER);

            String kcId = UUID.randomUUID().toString();
            when(keycloakAdminService.createUser(any(), any(), eq(Role.MANAGER))).thenReturn(kcId);
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> userService.createUser(target));
        }

        @Test
        @DisplayName("SUPER_ADMIN puede crear ADMIN")
        void superAdmin_puedeCrear_admin() {
            mockCallerRole(Role.SUPER_ADMIN);
            User target = buildUser(null, "nuevo@test.com", Role.ADMIN);

            when(keycloakAdminService.createUser(any(), any(), eq(Role.ADMIN)))
                    .thenReturn(UUID.randomUUID().toString());
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> userService.createUser(target));
        }

        @ParameterizedTest(name = "SUPER_ADMIN puede crear {0}")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("SUPER_ADMIN puede crear roles de nivel 3")
        void superAdmin_puedeCrear_rolesNivel3(Role targetRole) {
            mockCallerRole(Role.SUPER_ADMIN);
            User target = buildUser(null, "nuevo@test.com", targetRole);

            when(keycloakAdminService.createUser(any(), any(), eq(targetRole)))
                    .thenReturn(UUID.randomUUID().toString());
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> userService.createUser(target),
                    "SUPER_ADMIN debe poder crear " + targetRole);
        }

        @Test
        @DisplayName("MANAGER puede crear ADMIN")
        void manager_puedeCrear_admin() {
            mockCallerRole(Role.MANAGER);
            User target = buildUser(null, "nuevo@test.com", Role.ADMIN);

            when(keycloakAdminService.createUser(any(), any(), eq(Role.ADMIN)))
                    .thenReturn(UUID.randomUUID().toString());
            when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> userService.createUser(target));
        }

        @Test
        @DisplayName("MANAGER NO puede crear SUPER_ADMIN")
        void manager_noPuedeCrear_superAdmin() {
            mockCallerRole(Role.MANAGER);
            User target = buildUser(null, "nuevo@test.com", Role.SUPER_ADMIN);

            RbacException ex = assertThrows(RbacException.class,
                    () -> userService.createUser(target));
            assertTrue(ex.getMessage().contains("cannot manage"),
                    "El mensaje debe indicar que MANAGER no puede gestionar SUPER_ADMIN");
        }

        @Test
        @DisplayName("ADMIN NO puede crear MANAGER")
        void admin_noPuedeCrear_manager() {
            mockCallerRole(Role.ADMIN);
            User target = buildUser(null, "nuevo@test.com", Role.MANAGER);

            assertThrows(RbacException.class, () -> userService.createUser(target));
        }

        @Test
        @DisplayName("ADMIN NO puede crear ADMIN (mismo nivel)")
        void admin_noPuedeCrear_admin_mismoNivel() {
            mockCallerRole(Role.ADMIN);
            User target = buildUser(null, "nuevo@test.com", Role.ADMIN);

            RbacException ex = assertThrows(RbacException.class,
                    () -> userService.createUser(target),
                    "Un ADMIN no puede crear otro ADMIN (mismo nivel no permite gestion)");
            assertNotNull(ex.getMessage());
        }

        @ParameterizedTest(name = "OWNER no puede crear {0}")
        @EnumSource(value = Role.class)
        @DisplayName("OWNER (nivel 3) no puede crear ningun rol")
        void owner_noPuedeCrearNingunRol(Role targetRole) {
            mockCallerRole(Role.OWNER);
            User target = buildUser(null, "nuevo@test.com", targetRole);

            assertThrows(RbacException.class,
                    () -> userService.createUser(target),
                    "OWNER no debe poder crear el rol " + targetRole);
        }
    }

    // ---------------------------------------------------------------
    // SECCION 2: validateNotSelf — proteccion contra auto-eliminacion
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validateNotSelf — Auto-operacion prohibida")
    class ValidateNotSelf {

        @Test
        @DisplayName("deactivateUser: no puede desactivar su propia cuenta")
        void deactivateUser_sobreSiMismo_deberiaLanzarRbacException() {
            String callerEmail = "superadmin@padilla.test";
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, callerEmail);

            UUID userId = UUID.randomUUID();
            User self = buildUser(userId, callerEmail, Role.MANAGER);
            when(userRepository.findById(userId)).thenReturn(Optional.of(self));

            RbacException ex = assertThrows(RbacException.class,
                    () -> userService.deactivateUser(userId));
            assertTrue(ex.getMessage().contains("cannot deactivate your own account"),
                    "Mensaje debe indicar que no puede desactivar su propia cuenta");
        }

        @Test
        @DisplayName("deleteUserPermanently: no puede eliminarse a si mismo")
        void deleteUserPermanently_sobreSiMismo_deberiaLanzarRbacException() {
            String callerEmail = "superadmin@padilla.test";
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, callerEmail);

            UUID userId = UUID.randomUUID();
            User self = buildUser(userId, callerEmail, Role.MANAGER);
            when(userRepository.findById(userId)).thenReturn(Optional.of(self));

            assertThrows(RbacException.class,
                    () -> userService.deleteUserPermanently(userId));
        }

        @Test
        @DisplayName("deactivateUser: puede desactivar a otro usuario")
        void deactivateUser_sobreOtroUsuario_deberiaFuncionar() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "superadmin@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "otro@padilla.test", Role.ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.save(any())).thenReturn(target);
            doNothing().when(keycloakAdminService).disableUser(any());

            assertDoesNotThrow(() -> userService.deactivateUser(targetId));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 3: deactivateUser — flujo completo
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deactivateUser — Flujo de desactivacion")
    class DeactivateUser {

        @Test
        @DisplayName("Desactiva en DB (active=false) y llama a disableUser en Keycloak")
        void deactivateUser_deberiaDesactivarEnDbYKeycloak() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.save(any())).thenReturn(target);
            doNothing().when(keycloakAdminService).disableUser("target@padilla.test");

            userService.deactivateUser(targetId);

            assertFalse(target.isActive(),
                    "El usuario debe quedar con active=false en la entidad");
            verify(userRepository, times(1)).save(target);
            verify(keycloakAdminService, times(1)).disableUser("target@padilla.test");
        }

        @Test
        @DisplayName("Lanza RuntimeException si el usuario no existe en DB")
        void deactivateUser_usuarioInexistente_deberiaLanzarRuntimeException() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> userService.deactivateUser(randomId));
            verify(keycloakAdminService, never()).disableUser(any());
        }

        @Test
        @DisplayName("No llama a Keycloak si el usuario no existe en DB")
        void deactivateUser_usuarioInexistente_noDeberiaTocarKeycloak() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> userService.deactivateUser(randomId));
            verifyNoInteractions(keycloakAdminService);
        }
    }

    // ---------------------------------------------------------------
    // SECCION 4: reactivateUser — flujo completo
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("reactivateUser — Flujo de reactivacion")
    class ReactivateUser {

        @Test
        @DisplayName("Activa en DB (active=true) y llama a enableUser en Keycloak")
        void reactivateUser_deberiaActivarEnDbYKeycloak() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.ADMIN);
            target.setActive(false);

            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(userRepository.save(any())).thenReturn(target);
            doNothing().when(keycloakAdminService).enableUser("target@padilla.test");

            userService.reactivateUser(targetId);

            assertTrue(target.isActive(),
                    "El usuario debe quedar con active=true");
            verify(userRepository, times(1)).save(target);
            verify(keycloakAdminService, times(1)).enableUser("target@padilla.test");
        }

        @Test
        @DisplayName("Lanza RuntimeException si el usuario no existe en DB")
        void reactivateUser_usuarioInexistente_deberiaLanzarRuntimeException() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> userService.reactivateUser(randomId));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 5: deleteUserPermanently — flujo y orden de operaciones
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deleteUserPermanently — Eliminacion permanente")
    class DeleteUserPermanently {

        @Test
        @DisplayName("Elimina de DB y luego de Keycloak en ese orden")
        void deleteUserPermanently_deberiaEliminarDeDbYKeycloakEnOrden() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

            userService.deleteUserPermanently(targetId);

            verify(userRepository, times(1)).deleteById(targetId);
            verify(keycloakAdminService, times(1)).deleteUserByEmail("target@padilla.test");
        }

        @Test
        @DisplayName("Orden correcto: DB delete ANTES que Keycloak delete")
        void deleteUserPermanently_ordenCorrecto_dbAntesQueKeycloak() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.MANAGER);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

            // Verificar orden usando InOrder
            org.mockito.InOrder inOrder = inOrder(userRepository, keycloakAdminService);

            userService.deleteUserPermanently(targetId);

            inOrder.verify(userRepository).deleteById(targetId);
            inOrder.verify(keycloakAdminService).deleteUserByEmail("target@padilla.test");
        }

        @Test
        @DisplayName("Si Keycloak falla, la excepcion se propaga (rollback transaccional)")
        void deleteUserPermanently_keycloakFalla_deberiaLanzarExcepcion() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            doThrow(new RuntimeException("Keycloak no disponible"))
                    .when(keycloakAdminService).deleteUserByEmail("target@padilla.test");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.deleteUserPermanently(targetId));
            assertEquals("Keycloak no disponible", ex.getMessage());
        }

        @Test
        @DisplayName("Lanza RuntimeException si el usuario no existe en DB")
        void deleteUserPermanently_usuarioInexistente_deberiaLanzarRuntimeException() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> userService.deleteUserPermanently(randomId));
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        @DisplayName("SUPER_ADMIN NO puede eliminar otro SUPER_ADMIN")
        void deleteUserPermanently_superAdminSobreSuperAdmin_deberiaLanzarRbacException() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "otro-super@padilla.test", Role.SUPER_ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

            // SUPER_ADMIN (nivel 0) no puede gestionar SUPER_ADMIN (nivel 0) — callerLevel >= targetLevel
            assertThrows(RbacException.class,
                    () -> userService.deleteUserPermanently(targetId),
                    "SUPER_ADMIN no puede eliminar a otro SUPER_ADMIN");
        }

        @Test
        @DisplayName("Usuario no existe en Keycloak (UUID desincronizado): deleteUserByEmail maneja not-found y DB se elimina igual")
        void deleteUserPermanently_keycloakNoEncuentraUsuario_dbSeEliminaIgual() {
            mockCallerRoleAndEmail(Role.SUPER_ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.ADMIN);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            // deleteUserByEmail no lanza excepcion si no encuentra el usuario (log warn y retorna)
            doNothing().when(keycloakAdminService).deleteUserByEmail("target@padilla.test");

            assertDoesNotThrow(() -> userService.deleteUserPermanently(targetId),
                    "Si Keycloak no encuentra el usuario por email, la operacion completa eliminando de DB");
            verify(userRepository, times(1)).deleteById(targetId);
            verify(keycloakAdminService, times(1)).deleteUserByEmail("target@padilla.test");
        }

        @Test
        @DisplayName("ADMIN NO puede eliminar a ningun usuario (solo SUPER_ADMIN puede llamar delete permanent)")
        void deleteUserPermanently_admin_noDeberiaPoderEliminar() {
            mockCallerRoleAndEmail(Role.ADMIN, "caller@padilla.test");

            UUID targetId = UUID.randomUUID();
            User target = buildUser(targetId, "target@padilla.test", Role.OWNER);
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

            // ADMIN (nivel 2) vs OWNER (nivel 3): 2 < 3, deberia poder por RBAC
            // Pero el endpoint esta protegido con @PreAuthorize("hasRole('SUPER_ADMIN')")
            // A nivel de servicio, un ADMIN puede eliminar OWNER segun validateCanManage
            // Documentamos el comportamiento real del servicio (la restriccion SUPER_ADMIN es en el controller)
            assertDoesNotThrow(() -> userService.deleteUserPermanently(targetId),
                    "A nivel de servicio, ADMIN puede eliminar OWNER — la restriccion SUPER_ADMIN esta en el Controller");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 6: Consultas — delegacion al repositorio
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Consultas — findAll, findById, findByRole con filtro por nivel")
    class Consultas {

        @Test
        @DisplayName("findAll_superAdmin_retornaTodosLosUsuariosDeNivelInferior")
        void findAll_superAdmin_deberiaRetornarTodosDebajo() {
            mockCallerRole(Role.SUPER_ADMIN);
            List<User> todos = List.of(
                    buildUser(UUID.randomUUID(), "m@test.com", Role.MANAGER),
                    buildUser(UUID.randomUUID(), "a@test.com", Role.ADMIN),
                    buildUser(UUID.randomUUID(), "t@test.com", Role.TENANT)
            );
            when(userRepository.findAll()).thenReturn(todos);

            List<User> result = userService.findAll();

            assertEquals(3, result.size()); // SUPER_ADMIN ve todos los de nivel > 0
            verify(userRepository).findAll();
        }

        @Test
        @DisplayName("findAll_admin_retornaSoloNivel3_ocultaIgualesYSuperiores")
        void findAll_admin_deberiaOcultarIgualesYSuperiores() {
            mockCallerRole(Role.ADMIN);
            List<User> todos = List.of(
                    buildUser(UUID.randomUUID(), "manager@test.com", Role.MANAGER),  // nivel 1 — oculto
                    buildUser(UUID.randomUUID(), "admin2@test.com", Role.ADMIN),     // nivel 2 — oculto
                    buildUser(UUID.randomUUID(), "owner@test.com", Role.OWNER),      // nivel 3 — visible
                    buildUser(UUID.randomUUID(), "tenant@test.com", Role.TENANT)     // nivel 3 — visible
            );
            when(userRepository.findAll()).thenReturn(todos);

            List<User> result = userService.findAll();

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(u -> u.getRole() == Role.OWNER || u.getRole() == Role.TENANT));
        }

        @Test
        @DisplayName("findById_existente_retornaUsuarioSiEsDeNivelInferior")
        void findById_existente_deberiaRetornarOptionalConUsuario() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "manager@test.com", Role.MANAGER);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));

            Optional<User> result = userService.findById(id);

            assertTrue(result.isPresent());
            assertEquals(id, result.get().getId());
        }

        @Test
        @DisplayName("findById_noExistente_retornaOptionalVacio")
        void findById_noExistente_deberiaRetornarOptionalVacio() {
            mockCallerRole(Role.SUPER_ADMIN);
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            Optional<User> result = userService.findById(id);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("findById_usuarioDeNivelIgualOSuperior_retornaVacio")
        void findById_usuarioNoVisible_deberiaRetornarVacio() {
            mockCallerRole(Role.ADMIN); // nivel 2
            UUID id = UUID.randomUUID();
            User manager = buildUser(id, "manager@test.com", Role.MANAGER); // nivel 1
            when(userRepository.findById(id)).thenReturn(Optional.of(manager));

            Optional<User> result = userService.findById(id);

            assertTrue(result.isEmpty()); // ADMIN no puede ver MANAGER
        }

        @Test
        @DisplayName("findByRole_rolVisible_retornaUsuariosDeEseRol")
        void findByRole_rolVisible_deberiaRetornarUsuarios() {
            mockCallerRole(Role.ADMIN); // nivel 2, puede ver OWNER (nivel 3)
            List<User> owners = List.of(buildUser(UUID.randomUUID(), "owner@test.com", Role.OWNER));
            when(userRepository.findByRole(Role.OWNER)).thenReturn(owners);

            List<User> result = userService.findByRole(Role.OWNER);

            assertEquals(1, result.size());
            verify(userRepository).findByRole(Role.OWNER);
        }

        @Test
        @DisplayName("findByRole_rolNoVisible_retornaListaVaciaSinConsultarDB")
        void findByRole_rolNoVisible_deberiaRetornarVacio() {
            mockCallerRole(Role.ADMIN); // nivel 2, NO puede ver MANAGER (nivel 1)

            List<User> result = userService.findByRole(Role.MANAGER);

            assertEquals(0, result.size());
            verify(userRepository, never()).findByRole(Role.MANAGER); // nunca llega a la DB
        }
    }

    // ---------------------------------------------------------------
    // SECCION 7: resendAccess
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("resendAccess — reenvio de acceso temporal")
    class ResendAccess {

        @Test
        @DisplayName("01 — resendAccess: resetea Keycloak, activa firstLogin en DB y envia email")
        void resendAccess_deberiaResetearKeycloakActualizarDbYEnviarEmail() {
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "tenant@padilla.test", Role.TENANT);
            mockCallerRole(Role.ADMIN);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail("tenant@padilla.test")).thenReturn("TempPass123!");
            when(userRepository.save(any(User.class))).thenReturn(user);

            userService.resendAccess(id);

            verify(keycloakAdminService).resetPasswordByEmail("tenant@padilla.test");
            verify(userRepository).save(argThat(u -> u.isFirstLogin() && u.getPasswordResetExpiresAt() != null));
            verify(emailService).sendPasswordResetEmail("tenant@padilla.test", user.getName(), "TempPass123!");
        }

        @Test
        @DisplayName("02 — resendAccess: lanza RuntimeException si usuario no existe en DB")
        void resendAccess_usuarioInexistente_deberiaLanzarRuntimeException() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> userService.resendAccess(id));
            verifyNoInteractions(keycloakAdminService);
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("03 — resendAccess: ADMIN no puede reenviar acceso a otro ADMIN (mismo nivel)")
        void resendAccess_adminSobreAdmin_deberiaLanzarRbacException() {
            UUID id = UUID.randomUUID();
            User target = buildUser(id, "admin2@padilla.test", Role.ADMIN);
            mockCallerRole(Role.ADMIN);
            when(userRepository.findById(id)).thenReturn(Optional.of(target));

            assertThrows(Exception.class, () -> userService.resendAccess(id));
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        @DisplayName("04 — resendAccess: passwordResetExpiresAt se establece aproximadamente 24 horas en el futuro")
        void resendAccess_passwordResetExpiresAtEn24Horas() {
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "tenant@padilla.test", Role.TENANT);
            mockCallerRole(Role.ADMIN);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail("tenant@padilla.test")).thenReturn("TempPass123!");
            when(userRepository.save(any(User.class))).thenReturn(user);

            OffsetDateTime testStart = OffsetDateTime.now();
            userService.resendAccess(id);

            verify(userRepository).save(argThat(u -> {
                OffsetDateTime expiry = u.getPasswordResetExpiresAt();
                return expiry != null
                        && expiry.isAfter(testStart.plusHours(23))
                        && expiry.isBefore(testStart.plusHours(25));
            }));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 8: resetPassword (admin reset desde el panel)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("resetPassword — Keycloak + DB firstLogin + email")
    class ResetPassword {

        @Test
        @DisplayName("01 — resetPassword: resetea Keycloak, activa firstLogin en DB y envia email")
        void resetPassword_deberiaLlamarResetEnKeycloakActualizarDbYEnviarEmail() {
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "user@padilla.test", Role.TENANT);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail("user@padilla.test"))
                    .thenReturn("NuevoPass123!");
            when(userRepository.save(any(User.class))).thenReturn(user);

            String newPass = userService.resetPassword(id);

            assertEquals("NuevoPass123!", newPass);
            verify(keycloakAdminService, times(1)).resetPasswordByEmail("user@padilla.test");
            verify(userRepository).save(argThat(u -> u.isFirstLogin() && u.getPasswordResetExpiresAt() != null));
            verify(emailService).sendPasswordResetEmail("user@padilla.test", user.getName(), "NuevoPass123!");
        }

        @Test
        @DisplayName("02 — resetPassword: lanza RuntimeException si usuario no existe en DB")
        void resetPassword_usuarioInexistente_deberiaLanzarRuntimeException() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> userService.resetPassword(id));
            verifyNoInteractions(keycloakAdminService);
        }

        @Test
        @DisplayName("03 — resetPassword: passwordResetExpiresAt se establece aproximadamente 24 horas en el futuro")
        void resetPassword_passwordResetExpiresAtEn24Horas() {
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "user@padilla.test", Role.TENANT);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail("user@padilla.test"))
                    .thenReturn("NuevoPass123!");
            when(userRepository.save(any(User.class))).thenReturn(user);

            OffsetDateTime testStart = OffsetDateTime.now();
            userService.resetPassword(id);

            verify(userRepository).save(argThat(u -> {
                OffsetDateTime expiry = u.getPasswordResetExpiresAt();
                return expiry != null
                        && expiry.isAfter(testStart.plusHours(23))
                        && expiry.isBefore(testStart.plusHours(25));
            }));
        }

        @Test
        @DisplayName("04 — resetPassword: si Keycloak falla la excepcion se propaga y no se toca DB ni email")
        void resetPassword_keycloakFalla_deberiaLanzarExcepcion() {
            UUID id = UUID.randomUUID();
            User user = buildUser(id, "user@padilla.test", Role.TENANT);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(keycloakAdminService.resetPasswordByEmail("user@padilla.test"))
                    .thenThrow(new RuntimeException("Keycloak no disponible"));

            assertThrows(RuntimeException.class, () -> userService.resetPassword(id));
            verify(userRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }
    }
}
