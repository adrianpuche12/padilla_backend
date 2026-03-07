package com.padilla.backend.repository;

import com.padilla.backend.entity.Contract;
import com.padilla.backend.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByPropertyId(UUID propertyId);

    List<Contract> findByOwnerId(UUID ownerId);

    List<Contract> findByTenantId(UUID tenantId);

    List<Contract> findByStatus(ContractStatus status);

    List<Contract> findByOwnerIdAndActive(UUID ownerId, boolean active);

    List<Contract> findByTenantIdAndActive(UUID tenantId, boolean active);

    Optional<Contract> findByPropertyIdAndStatus(UUID propertyId, ContractStatus status);
}
