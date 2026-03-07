package com.padilla.backend.repository;

import com.padilla.backend.entity.Property;
import com.padilla.backend.enums.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findByOwnerId(UUID ownerId);

    List<Property> findByTenantId(UUID tenantId);

    List<Property> findByStatus(PropertyStatus status);

    List<Property> findByActive(boolean active);

    List<Property> findByOwnerIdAndActive(UUID ownerId, boolean active);

    List<Property> findByTenantIdAndActive(UUID tenantId, boolean active);
}
