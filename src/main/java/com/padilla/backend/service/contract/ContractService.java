package com.padilla.backend.service.contract;


import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.ContractPeriod;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.ContractPeriodRepository;
import com.padilla.backend.repository.ContractRepository;
import com.padilla.backend.service.property.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final com.padilla.backend.service.audit.AuditService auditService;

    private final ContractRepository contractRepository;
    private final ContractPeriodRepository contractPeriodRepository;
    private final PropertyService propertyService;

    // --- Consultas ---

    public List<Contract> findAll(ContractStatus status, UUID propertyId) {
        Role callerRole = CurrentUser.role();

        if (isAdmin(callerRole)) {
            if (propertyId != null) return contractRepository.findByPropertyId(propertyId);
            if (status != null) return contractRepository.findByStatus(status);
            return contractRepository.findAll();
        }

        UUID callerId = CurrentUser.id();

        if (callerRole == Role.OWNER) {
            return contractRepository.findByOwnerIdAndActive(callerId, true);
        }

        if (callerRole == Role.TENANT) {
            return contractRepository.findByTenantIdAndActive(callerId, true);
        }

        throw new RbacException("Role " + callerRole + " has no access to contracts");
    }

    public Optional<Contract> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        Optional<Contract> contract = contractRepository.findById(id);

        if (isAdmin(callerRole)) return contract;

        UUID callerId = CurrentUser.id();

        return contract.filter(c -> {
            if (callerRole == Role.OWNER) return callerId.equals(c.getOwnerId());
            if (callerRole == Role.TENANT) return callerId.equals(c.getTenantId());
            return false;
        });
    }

    // --- Mutaciones ---

    @Transactional
    public Contract create(Contract contract) {
        validateIsAdmin();

        // Verificar que la propiedad no tenga ya un contrato ACTIVE
        contractRepository.findByPropertyIdAndStatus(contract.getPropertyId(), ContractStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Property already has an active contract: " + existing.getId()
                    );
                });

        Contract saved = contractRepository.save(contract);

        // Guardar el primer período de indexación automáticamente
        ContractPeriod firstPeriod = new ContractPeriod();
        firstPeriod.setContractId(saved.getId());
        firstPeriod.setPeriodFrom(saved.getStartDate());
        firstPeriod.setRentAmount(saved.getMonthlyAmount());
        if (saved.getCommissionPct() != null) {
            firstPeriod.setCommissionAmount(
                saved.getMonthlyAmount().multiply(saved.getCommissionPct())
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
            );
        }
        if (saved.getAdminFeePct() != null) {
            firstPeriod.setAdminFeeAmount(
                saved.getMonthlyAmount().multiply(saved.getAdminFeePct())
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
            );
        }
        firstPeriod.setAdjustmentIndex(java.math.BigDecimal.ZERO);
        contractPeriodRepository.saveAndFlush(firstPeriod);

        // Actualizar el tenantId en la propiedad
        propertyService.assignTenant(saved.getPropertyId(), saved.getTenantId());

        return saved;
    }

    public List<ContractPeriod> findPeriodsByContractId(UUID contractId) {
        Role callerRole = CurrentUser.role();
        Optional<Contract> contract = findById(contractId);
        if (contract.isEmpty()) {
            throw new RbacException("Contrato no encontrado o sin acceso");
        }
        return contractPeriodRepository.findByContractIdOrderByPeriodFromAsc(contractId);
    }

    @Transactional
    public Contract update(UUID id, java.math.BigDecimal monthlyAmount, String currency, java.time.LocalDate endDate) {
        validateIsAdmin();
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contrato no encontrado: " + id));

        if (monthlyAmount != null) contract.setMonthlyAmount(monthlyAmount);
        if (currency != null) contract.setCurrency(currency);
        if (endDate != null) contract.setEndDate(endDate);

        return contractRepository.save(contract);
    }

    @Transactional
    public void terminate(UUID id) {
        validateIsAdmin();
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Contrato no encontrado: " + id));

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setActive(false);
        contractRepository.save(contract);

        // Limpiar el tenantId de la propiedad
        auditService.record("CONTRATO_RESCINDIDO", "CONTRATO", contract.getId(), null);
        propertyService.clearTenant(contract.getPropertyId());
    }

    // --- RBAC helpers ---

    private boolean isAdmin(Role role) {
        return role.level() <= Role.ADMIN.level();
    }

    private void validateIsAdmin() {
        Role callerRole = CurrentUser.role();
        if (!isAdmin(callerRole)) {
            throw new RbacException("Role " + callerRole + " cannot modify contracts");
        }
    }


}
