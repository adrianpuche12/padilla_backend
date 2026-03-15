package com.padilla.backend.service.contract;

import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.ContractPeriod;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.ContractPeriodRepository;
import com.padilla.backend.repository.ContractRepository;
import com.padilla.backend.service.property.PropertyService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TESTS UNITARIOS — ContractService
 *
 * No requiere DB ni sistemas externos. Todo esta mockeado con Mockito.
 *
 * Cubre:
 *   RBAC crear     — SUPER_ADMIN, MANAGER, ADMIN pueden; OWNER, TENANT, PROVIDER no pueden
 *   RBAC editar    — idem
 *   RBAC rescindir — idem
 *   RBAC consultas — roles privilegiados ven todo; OWNER/TENANT solo los suyos; PROVIDER bloqueado
 *   Restriccion    — propiedad con contrato ACTIVE no puede tener otro ACTIVE
 *   Primer periodo — se crea automaticamente al crear el contrato con valores correctos
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContractService — Logica de negocio y RBAC")
class ContractServiceTest {

    @Mock private ContractRepository       contractRepository;
    @Mock private ContractPeriodRepository contractPeriodRepository;
    @Mock private PropertyService          propertyService;

    @InjectMocks
    private ContractService contractService;

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

    private Contract buildContract() {
        Contract c = new Contract();
        c.setPropertyId(UUID.randomUUID());
        c.setOwnerId(UUID.randomUUID());
        c.setTenantId(UUID.randomUUID());
        c.setStartDate(LocalDate.of(2025, 1, 1));
        c.setEndDate(LocalDate.of(2027, 1, 1));
        c.setMonthlyAmount(new BigDecimal("100000.00"));
        c.setCurrency("ARS");
        c.setStatus(ContractStatus.ACTIVE);
        c.setActive(true);
        return c;
    }

    private Contract savedContract(Contract base) {
        base.setId(UUID.randomUUID());
        return base;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------
    // SECCION 1: RBAC — create
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — crear contrato")
    class RbacCrear {

        @ParameterizedTest(name = "{0} puede crear un contrato")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden crear contratos")
        void rolesPrivilegiados_puedenCrear(Role role) {
            mockCallerRole(role);
            Contract contrato = buildContract();

            when(contractRepository.findByPropertyIdAndStatus(any(), eq(ContractStatus.ACTIVE)))
                    .thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            assertDoesNotThrow(() -> contractService.create(contrato),
                    role + " deberia poder crear contratos");
        }

        @ParameterizedTest(name = "{0} no puede crear un contrato")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden crear contratos")
        void rolesNoPivilegados_noPuedenCrear(Role role) {
            mockCallerRole(role);
            Contract contrato = buildContract();

            assertThrows(RbacException.class, () -> contractService.create(contrato),
                    role + " no deberia poder crear contratos");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 2: RBAC — update
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — editar contrato")
    class RbacEditar {

        @ParameterizedTest(name = "{0} puede editar un contrato")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden editar contratos")
        void rolesPrivilegiados_puedenEditar(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();
            Contract existente = buildContract();
            existente.setId(id);

            when(contractRepository.findById(id)).thenReturn(Optional.of(existente));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() -> contractService.update(id, new BigDecimal("120000.00"), "ARS", null),
                    role + " deberia poder editar contratos");
        }

        @ParameterizedTest(name = "{0} no puede editar un contrato")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden editar contratos")
        void rolesNoPrivilegiados_noPuedenEditar(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();

            assertThrows(RbacException.class,
                    () -> contractService.update(id, new BigDecimal("120000.00"), "ARS", null),
                    role + " no deberia poder editar contratos");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 3: RBAC — terminate
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — rescindir contrato")
    class RbacRescindir {

        @ParameterizedTest(name = "{0} puede rescindir un contrato")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados pueden rescindir contratos")
        void rolesPrivilegiados_puedenRescindir(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();
            Contract existente = buildContract();
            existente.setId(id);

            when(contractRepository.findById(id)).thenReturn(Optional.of(existente));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).clearTenant(any());

            assertDoesNotThrow(() -> contractService.terminate(id),
                    role + " deberia poder rescindir contratos");
        }

        @ParameterizedTest(name = "{0} no puede rescindir un contrato")
        @EnumSource(value = Role.class, names = {"OWNER", "TENANT", "PROVIDER"})
        @DisplayName("Roles no privilegiados no pueden rescindir contratos")
        void rolesNoPrivilegiados_noPuedenRescindir(Role role) {
            mockCallerRole(role);
            UUID id = UUID.randomUUID();

            assertThrows(RbacException.class,
                    () -> contractService.terminate(id),
                    role + " no deberia poder rescindir contratos");
        }
    }

    // ---------------------------------------------------------------
    // SECCION 4: RBAC — consultas (findAll)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("RBAC — consultar contratos")
    class RbacConsultar {

        @ParameterizedTest(name = "{0} ve todos los contratos")
        @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "MANAGER", "ADMIN"})
        @DisplayName("Roles privilegiados ven todos los contratos sin filtro")
        void rolesPrivilegiados_venTodos(Role role) {
            mockCallerRole(role);
            when(contractRepository.findAll()).thenReturn(List.of(buildContract()));

            List<?> result = contractService.findAll(null, null);

            assertFalse(result.isEmpty());
            verify(contractRepository).findAll();
        }

        @Test
        @DisplayName("OWNER solo ve sus propios contratos activos")
        void owner_soloveContratosPropios() {
            UUID ownerId = UUID.randomUUID();
            mockCallerRoleWithId(Role.OWNER, ownerId.toString());

            Contract propio = buildContract();
            propio.setOwnerId(ownerId);
            when(contractRepository.findByOwnerIdAndActive(ownerId, true))
                    .thenReturn(List.of(propio));

            List<?> result = contractService.findAll(null, null);

            assertFalse(result.isEmpty());
            verify(contractRepository).findByOwnerIdAndActive(ownerId, true);
            verify(contractRepository, never()).findAll();
        }

        @Test
        @DisplayName("TENANT solo ve sus propios contratos activos")
        void tenant_soloveContratosPropios() {
            UUID tenantId = UUID.randomUUID();
            mockCallerRoleWithId(Role.TENANT, tenantId.toString());

            Contract propio = buildContract();
            propio.setTenantId(tenantId);
            when(contractRepository.findByTenantIdAndActive(tenantId, true))
                    .thenReturn(List.of(propio));

            List<?> result = contractService.findAll(null, null);

            assertFalse(result.isEmpty());
            verify(contractRepository).findByTenantIdAndActive(tenantId, true);
            verify(contractRepository, never()).findAll();
        }

        @Test
        @DisplayName("PROVIDER no tiene acceso a contratos — lanza RbacException")
        void provider_noPuedeVerContratos() {
            mockCallerRole(Role.PROVIDER);

            assertThrows(RbacException.class, () -> contractService.findAll(null, null));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 5: Restriccion de propiedad
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Restriccion — una propiedad, un contrato ACTIVE")
    class RestriccionPropiedad {

        @Test
        @DisplayName("No se puede crear contrato si la propiedad ya tiene uno ACTIVE")
        void crear_propiedadConContratoActivo_deberiaLanzarException() {
            mockCallerRole(Role.ADMIN);
            Contract nuevo = buildContract();
            Contract existente = buildContract();
            existente.setId(UUID.randomUUID());

            when(contractRepository.findByPropertyIdAndStatus(nuevo.getPropertyId(), ContractStatus.ACTIVE))
                    .thenReturn(Optional.of(existente));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> contractService.create(nuevo));

            assertTrue(ex.getMessage().contains("already has an active contract"));
            verify(contractRepository, never()).save(any());
        }

        @Test
        @DisplayName("Se puede crear contrato si la propiedad no tiene ninguno ACTIVE")
        void crear_propiedadSinContratoActivo_deberiaCrearCorrecamente() {
            mockCallerRole(Role.ADMIN);
            Contract nuevo = buildContract();

            when(contractRepository.findByPropertyIdAndStatus(any(), eq(ContractStatus.ACTIVE)))
                    .thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            assertDoesNotThrow(() -> contractService.create(nuevo));
            verify(contractRepository).save(any());
        }
    }

    // ---------------------------------------------------------------
    // SECCION 6: Primer periodo automatico
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Primer periodo — creacion automatica al crear contrato")
    class PrimerPeriodo {

        @Test
        @DisplayName("El primer periodo tiene period_from igual a start_date del contrato")
        void primerPeriodo_periodFromIgualAStartDate() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            contractService.create(contrato);

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getPeriodFrom().equals(contrato.getStartDate())
            ));
        }

        @Test
        @DisplayName("El primer periodo tiene rent_amount igual a monthly_amount del contrato")
        void primerPeriodo_rentAmountIgualAMonthlyAmount() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            contractService.create(contrato);

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getRentAmount().compareTo(contrato.getMonthlyAmount()) == 0
            ));
        }

        @Test
        @DisplayName("El primer periodo tiene adjustment_index igual a 0")
        void primerPeriodo_adjustmentIndexCero() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            contractService.create(contrato);

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getAdjustmentIndex().compareTo(BigDecimal.ZERO) == 0
            ));
        }

        @Test
        @DisplayName("commission_amount se calcula correctamente: monthly_amount x commissionPct / 100")
        void primerPeriodo_commissionAmountCalculadoCorrectamente() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();
            contrato.setMonthlyAmount(new BigDecimal("100000.00"));
            contrato.setCommissionPct(new BigDecimal("8.00"));  // espera: 8000.00

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            contractService.create(contrato);

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getCommissionAmount().compareTo(new BigDecimal("8000.00")) == 0
            ));
        }

        @Test
        @DisplayName("admin_fee_amount se calcula correctamente: monthly_amount x adminFeePct / 100")
        void primerPeriodo_adminFeeAmountCalculadoCorrectamente() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();
            contrato.setMonthlyAmount(new BigDecimal("100000.00"));
            contrato.setAdminFeePct(new BigDecimal("2.00"));  // espera: 2000.00

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            contractService.create(contrato);

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getAdminFeeAmount().compareTo(new BigDecimal("2000.00")) == 0
            ));
        }

        @Test
        @DisplayName("Si commissionPct es null, commission_amount no se calcula y queda null")
        void primerPeriodo_sinCommissionPct_commissionAmountEsNull() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();
            contrato.setCommissionPct(null);

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            assertDoesNotThrow(() -> contractService.create(contrato));

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getCommissionAmount() == null
            ));
        }

        @Test
        @DisplayName("Si adminFeePct es null, admin_fee_amount no se calcula y queda null")
        void primerPeriodo_sinAdminFeePct_adminFeeAmountEsNull() {
            mockCallerRole(Role.ADMIN);
            Contract contrato = buildContract();
            contrato.setAdminFeePct(null);

            when(contractRepository.findByPropertyIdAndStatus(any(), any())).thenReturn(Optional.empty());
            when(contractRepository.save(any())).thenAnswer(i -> savedContract(i.getArgument(0)));
            when(contractPeriodRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).assignTenant(any(), any());

            assertDoesNotThrow(() -> contractService.create(contrato));

            verify(contractPeriodRepository).saveAndFlush(argThat(p ->
                    p.getAdminFeeAmount() == null
            ));
        }
    }

    // ---------------------------------------------------------------
    // SECCION 7: terminate — efectos sobre el contrato
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("terminate — efectos sobre el contrato y la propiedad")
    class Terminate {

        @Test
        @DisplayName("Al rescindir, el contrato queda con status TERMINATED y active=false")
        void terminate_contratoQuedaTerminado() {
            mockCallerRole(Role.ADMIN);
            UUID id = UUID.randomUUID();
            Contract existente = buildContract();
            existente.setId(id);

            when(contractRepository.findById(id)).thenReturn(Optional.of(existente));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).clearTenant(any());

            contractService.terminate(id);

            verify(contractRepository).save(argThat(c ->
                    c.getStatus() == ContractStatus.TERMINATED && !c.isActive()
            ));
        }

        @Test
        @DisplayName("Al rescindir, se limpia el tenantId de la propiedad")
        void terminate_limpiaElTenantDeLaPropiedad() {
            mockCallerRole(Role.ADMIN);
            UUID id = UUID.randomUUID();
            Contract existente = buildContract();
            existente.setId(id);
            UUID propertyId = existente.getPropertyId();

            when(contractRepository.findById(id)).thenReturn(Optional.of(existente));
            when(contractRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            doNothing().when(propertyService).clearTenant(any());

            contractService.terminate(id);

            verify(propertyService).clearTenant(propertyId);
        }

        @Test
        @DisplayName("Rescindir un contrato inexistente lanza RuntimeException")
        void terminate_contratoNoExiste_deberiaLanzarException() {
            mockCallerRole(Role.ADMIN);
            UUID id = UUID.randomUUID();
            when(contractRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> contractService.terminate(id));
        }
    }
}
