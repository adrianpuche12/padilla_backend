package com.padilla.backend.service.property;

import com.padilla.backend.entity.Property;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.PropertyType;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.PropertyRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TESTS UNITARIOS — PropertyService
 *
 * No requiere DB ni sistemas externos. Todo esta mockeado con Mockito.
 *
 * Cubre:
 *   RBAC crear     — SUPER_ADMIN, MANAGER, ADMIN pueden; OWNER, TENANT, PROVIDER no pueden
 *   RBAC editar    — idem
 *   RBAC desactivar — idem
 *   RBAC consultas — roles privilegiados ven todo; OWNER/TENANT solo los suyos; PROVIDER bloqueado
 *   assignTenant   — asigna tenantId y cambia status a RENTED
 *   clearTenant    — limpia tenantId y cambia status a AVAILABLE
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PropertyService — Logica de negocio y RBAC")
class PropertyServiceTest {

    @Mock private PropertyRepository propertyRepository;

    @InjectMocks
    private PropertyService propertyService;

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void mockCallerRole(Role role) {
        mockCallerRoleWithId(role, UUID.randomUUID().toString());
    }

    private void mockCallerRoleWithId(Role role, String callerId) {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx  = mock(SecurityContext.class);

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getAuthorities()).thenAnswer(i -> authorities);
        lenient().when(auth.getName()).thenReturn(callerId);

        SecurityContextHolder.setContext(ctx);
    }

    private Property buildProperty() {
        Property p = new Property();
        p.setId(UUID.randomUUID());
        p.setAddress("Laprida 514, Rosario");
        p.setType(PropertyType.DEPARTAMENTO);
        p.setOwnerId(UUID.randomUUID());
        p.setStatus(PropertyStatus.AVAILABLE);
        p.setActive(true);
        return p;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------
    // SECCION 1: RBAC — create
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — crear propiedad")
    class RbacCrear {

        @ParameterizedTest(name = "{0} puede crear una propiedad")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden crear propiedades")
        void rolesPrivilegiados_puedenCrear(Role role) {
            mockCallerRole(role);
            Property propiedad = buildProperty();
            when(propertyRepository.save(any())).thenReturn(propiedad);

            assertDoesNotThrow(() -> propertyService.create(propiedad),
                    role + " deberia poder crear propiedades");
        }

        @ParameterizedTest(name = "{0} no puede crear una propiedad")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden crear propiedades")
        void rolesNoPrivilegiados_noPuedenCrear(Role role) {
            mockCallerRole(role);
            Property propiedad = buildProperty();

            assertThrows(RbacException.class, () -> propertyService.create(propiedad),
                    role + " no deberia poder crear propiedades");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 2: RBAC — update
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — editar propiedad")
    class RbacEditar {

        @ParameterizedTest(name = "{0} puede editar una propiedad")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden editar propiedades")
        void rolesPrivilegiados_puedenEditar(Role role) {
            mockCallerRole(role);
            Property existente = buildProperty();
            when(propertyRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
            when(propertyRepository.save(any())).thenReturn(existente);

            assertDoesNotThrow(() -> propertyService.update(existente.getId(), "Nueva dirección", null, null, null),
                    role + " deberia poder editar propiedades");
        }

        @ParameterizedTest(name = "{0} no puede editar una propiedad")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden editar propiedades")
        void rolesNoPrivilegiados_noPuedenEditar(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();

            assertThrows(RbacException.class,
                    () -> propertyService.update(id, "Nueva dirección", null, null, null),
                    role + " no deberia poder editar propiedades");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 3: RBAC — deactivate
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — desactivar propiedad")
    class RbacDesactivar {

        @ParameterizedTest(name = "{0} puede desactivar una propiedad")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden desactivar propiedades")
        void rolesPrivilegiados_puedenDesactivar(Role role) {
            mockCallerRole(role);
            Property existente = buildProperty();
            when(propertyRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
            when(propertyRepository.save(any())).thenReturn(existente);

            assertDoesNotThrow(() -> propertyService.deactivate(existente.getId()),
                    role + " deberia poder desactivar propiedades");
        }

        @ParameterizedTest(name = "{0} no puede desactivar una propiedad")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden desactivar propiedades")
        void rolesNoPrivilegiados_noPuedenDesactivar(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();

            assertThrows(RbacException.class,
                    () -> propertyService.deactivate(id),
                    role + " no deberia poder desactivar propiedades");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 4: RBAC — consultas (findAll)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — consultar propiedades")
    class RbacConsultar {

        @ParameterizedTest(name = "{0} ve todas las propiedades")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados ven todas las propiedades")
        void rolesPrivilegiados_venTodas(Role role) {
            mockCallerRole(role);
            when(propertyRepository.findAll()).thenReturn(List.of(buildProperty()));

            List<?> result = propertyService.findAll(null);

            assertFalse(result.isEmpty());
            verify(propertyRepository).findAll();
        }

        @Test
        @DisplayName("OWNER solo ve sus propias propiedades activas")
        void owner_solovesSusPropiedades() {
            UUID ownerId = UUID.randomUUID();
            mockCallerRoleWithId(Role.OWNER, ownerId.toString());

            Property propia = buildProperty();
            propia.setOwnerId(ownerId);
            when(propertyRepository.findByOwnerIdAndActive(ownerId, true)).thenReturn(List.of(propia));

            List<?> result = propertyService.findAll(null);

            assertFalse(result.isEmpty());
            verify(propertyRepository).findByOwnerIdAndActive(ownerId, true);
            verify(propertyRepository, never()).findAll();
        }

        @Test
        @DisplayName("TENANT solo ve sus propias propiedades activas")
        void tenant_solovesSusPropiedades() {
            UUID tenantId = UUID.randomUUID();
            mockCallerRoleWithId(Role.TENANT, tenantId.toString());

            Property propia = buildProperty();
            propia.setTenantId(tenantId);
            when(propertyRepository.findByTenantIdAndActive(tenantId, true)).thenReturn(List.of(propia));

            List<?> result = propertyService.findAll(null);

            assertFalse(result.isEmpty());
            verify(propertyRepository).findByTenantIdAndActive(tenantId, true);
            verify(propertyRepository, never()).findAll();
        }

        @Test
        @DisplayName("PROVIDER no tiene acceso a propiedades — lanza RbacException")
        void provider_noPuedeVerPropiedades() {
            mockCallerRole(Role.PROVIDER);

            assertThrows(RbacException.class, () -> propertyService.findAll(null));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 5: deactivate / reactivate — efectos
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deactivate / reactivate — efectos sobre la propiedad")
    class ActivacionDesactivacion {

        @Test
        @DisplayName("Al desactivar, la propiedad queda con active=false y status=MAINTENANCE")
        void deactivate_propiedadQuedaInactiva() {
            mockCallerRole(Role.ADMIN);
            Property existente = buildProperty();

            when(propertyRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
            when(propertyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            propertyService.deactivate(existente.getId());

            verify(propertyRepository).save(argThat(p ->
                    !p.isActive() && p.getStatus() == PropertyStatus.MAINTENANCE
            ));
        }

        @Test
        @DisplayName("Al reactivar, la propiedad queda con active=true y status=AVAILABLE")
        void reactivate_propiedadVuelveAEstarDisponible() {
            mockCallerRole(Role.ADMIN);
            Property existente = buildProperty();
            existente.setActive(false);
            existente.setStatus(PropertyStatus.MAINTENANCE);

            when(propertyRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
            when(propertyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            propertyService.reactivate(existente.getId());

            verify(propertyRepository).save(argThat(p ->
                    p.isActive() && p.getStatus() == PropertyStatus.AVAILABLE
            ));
        }

        @ParameterizedTest(name = "{0} no puede reactivar una propiedad")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden reactivar propiedades")
        void reactivate_rolesNoPrivilegiados_lanzaRbacException(Role role) {
            mockCallerRole(role);
            assertThrows(RbacException.class, () -> propertyService.reactivate(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Desactivar propiedad inexistente lanza RuntimeException")
        void deactivate_propiedadNoExiste_deberiaLanzarException() {
            mockCallerRole(Role.ADMIN);
            UUID id = UUID.randomUUID();
            when(propertyRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> propertyService.deactivate(id));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 6: assignTenant y clearTenant (helpers de ContractService)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("assignTenant / clearTenant — integracion con contratos")
    class TenantHelpers {

        @Test
        @DisplayName("assignTenant asigna el tenantId y cambia status a RENTED")
        void assignTenant_asignaTenantYCambiaStatus() {
            Property propiedad = buildProperty();
            UUID tenantId = UUID.randomUUID();

            when(propertyRepository.findById(propiedad.getId())).thenReturn(Optional.of(propiedad));
            when(propertyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            propertyService.assignTenant(propiedad.getId(), tenantId);

            verify(propertyRepository).save(argThat(p ->
                    tenantId.equals(p.getTenantId()) && p.getStatus() == PropertyStatus.RENTED
            ));
        }

        @Test
        @DisplayName("clearTenant limpia el tenantId y cambia status a AVAILABLE")
        void clearTenant_limpiaTenantyStatusVuelveAAvailable() {
            Property propiedad = buildProperty();
            propiedad.setTenantId(UUID.randomUUID());
            propiedad.setStatus(PropertyStatus.RENTED);

            when(propertyRepository.findById(propiedad.getId())).thenReturn(Optional.of(propiedad));
            when(propertyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            propertyService.clearTenant(propiedad.getId());

            verify(propertyRepository).save(argThat(p ->
                    p.getTenantId() == null && p.getStatus() == PropertyStatus.AVAILABLE
            ));
        }
    }
}
