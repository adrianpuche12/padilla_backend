package com.padilla.backend.service.property;


import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.entity.Property;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.PropertyRepository;
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
public class PropertyService {

    private final com.padilla.backend.service.settings.AgencySettingsService agencySettingsService;

    private final PropertyRepository propertyRepository;

    // --- Consultas ---

    public List<Property> findAll(PropertyStatus status) {
        Role callerRole = CurrentUser.role();

        if (isAdmin(callerRole)) {
            return (status != null)
                    ? propertyRepository.findByStatus(status)
                    : propertyRepository.findAll();
        }

        UUID callerId = CurrentUser.id();

        if (callerRole == Role.OWNER) {
            return propertyRepository.findByOwnerIdAndActive(callerId, true);
        }

        if (callerRole == Role.TENANT) {
            return propertyRepository.findByTenantIdAndActive(callerId, true);
        }

        throw new RbacException("Role " + callerRole + " has no access to properties");
    }

    public Optional<Property> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        Optional<Property> property = propertyRepository.findById(id);

        if (isAdmin(callerRole)) {
            return property;
        }

        UUID callerId = CurrentUser.id();

        return property.filter(p -> {
            if (callerRole == Role.OWNER) return callerId.equals(p.getOwnerId());
            if (callerRole == Role.TENANT) return callerId.equals(p.getTenantId());
            return false;
        });
    }

    // --- Mutaciones (solo ADMIN+) ---

    @Transactional
    public Property create(Property property) {
        // PRO-025: limite de propiedades activas del plan
        agencySettingsService.checkCanAddProperty();
        validateIsAdmin();
        return propertyRepository.save(property);
    }

    @Transactional
    public Property update(UUID id, String address, Double squareMeters, Integer rooms, PropertyStatus status) {
        validateIsAdmin();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + id));

        if (address != null) property.setAddress(address);
        if (squareMeters != null) property.setSquareMeters(squareMeters);
        if (rooms != null) property.setRooms(rooms);
        if (status != null) property.setStatus(status);

        return propertyRepository.save(property);
    }

    @Transactional
    public void deactivate(UUID id) {
        validateIsAdmin();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + id));
        property.setActive(false);
        property.setStatus(PropertyStatus.MAINTENANCE);
        propertyRepository.save(property);
    }

    @Transactional
    public void reactivate(UUID id) {
        validateIsAdmin();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + id));
        property.setActive(true);
        property.setStatus(PropertyStatus.AVAILABLE);
        propertyRepository.save(property);
    }

    // --- Helpers internos (usados por ContractService) ---

    @Transactional
    public void assignTenant(UUID propertyId, UUID tenantId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + propertyId));
        property.setTenantId(tenantId);
        property.setStatus(PropertyStatus.RENTED);
        propertyRepository.save(property);
    }

    @Transactional
    public void clearTenant(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + propertyId));
        property.setTenantId(null);
        property.setStatus(PropertyStatus.AVAILABLE);
        propertyRepository.save(property);
    }

    // --- RBAC helpers ---

    private boolean isAdmin(Role role) {
        return role.level() <= Role.ADMIN.level();
    }

    private void validateIsAdmin() {
        Role callerRole = CurrentUser.role();
        if (!isAdmin(callerRole)) {
            throw new RbacException("Role " + callerRole + " cannot modify properties");
        }
    }


}
