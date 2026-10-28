package com.padilla.backend.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.padilla.backend.entity.Property;
import com.padilla.backend.enums.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    List<Property> findByOwnerId(UUID ownerId);

    List<Property> findByTenantId(UUID tenantId);

    List<Property> findByStatus(PropertyStatus status);

    List<Property> findByActive(boolean active);

    List<Property> findByOwnerIdAndActive(UUID ownerId, boolean active);

    List<Property> findByTenantIdAndActive(UUID tenantId, boolean active);
}
