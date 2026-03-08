package com.padilla.backend.controller;

import com.padilla.backend.dto.contract.ContractDTO;
import com.padilla.backend.dto.contract.CreateContractRequest;
import com.padilla.backend.dto.contract.UpdateContractRequest;
import com.padilla.backend.entity.Contract;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.service.contract.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER','TENANT')")
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public ResponseEntity<List<ContractDTO>> getContracts(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) UUID propertyId) {
        List<Contract> contracts = contractService.findAll(status, propertyId);
        return ResponseEntity.ok(contracts.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractDTO> getContract(@PathVariable UUID id) {
        return contractService.findById(id)
                .map(c -> ResponseEntity.ok(toDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ContractDTO> createContract(@Valid @RequestBody CreateContractRequest request) {
        Contract contract = new Contract();
        contract.setPropertyId(request.getPropertyId());
        contract.setOwnerId(request.getOwnerId());
        contract.setTenantId(request.getTenantId());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setMonthlyAmount(request.getMonthlyAmount());
        contract.setCurrency(request.getCurrency() != null ? request.getCurrency() : "ARS");
        contract.setStatus(ContractStatus.ACTIVE);

        Contract created = contractService.create(contract);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ContractDTO> updateContract(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContractRequest request) {

        Contract updated = contractService.update(
                id,
                request.getMonthlyAmount(),
                request.getCurrency(),
                request.getEndDate()
        );
        return ResponseEntity.ok(toDTO(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> terminateContract(@PathVariable UUID id) {
        contractService.terminate(id);
        return ResponseEntity.ok().build();
    }

    private ContractDTO toDTO(Contract c) {
        return new ContractDTO(
                c.getId(),
                c.getPropertyId(),
                c.getOwnerId(),
                c.getTenantId(),
                c.getStartDate(),
                c.getEndDate(),
                c.getMonthlyAmount(),
                c.getCurrency(),
                c.getStatus(),
                c.isActive(),
                c.getCreatedAt()
        );
    }
}
