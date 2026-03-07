package com.padilla.backend.service.property;

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

    private final PropertyRepository propertyRepository;

    private static final Map<Role, Integer> ROLE_LEVEL = Map.of(
            Role.SUPER_ADMIN, 0,
            Role.MANAGER,     1,
            Role.ADMIN,       2,
            Role.OWNER,       3,
            Role.TENANT,      3,
            Role.PROVIDER,    3
    );

    // --- Consultas ---

    public List<Property> findAll(PropertyStatus status) {
        Role callerRole = getCurrentUserRole();

        if (isAdmin(callerRole)) {
            return (status != null)
                    ? propertyRepository.findByStatus(status)
                    : propertyRepository.findAll();
        }

        UUID callerId = getCurrentUserId();

        if (callerRole == Role.OWNER) {
            return propertyRepository.findByOwnerIdAndActive(callerId, true);
        }

        if (callerRole == Role.TENANT) {
            return propertyRepository.findByTenantIdAndActive(callerId, true);
        }

        throw new RbacException("Role " + callerRole + " has no access to properties");
    }

    public Optional<Property> findById(UUID id) {
        Role callerRole = getCurrentUserRole();
        Optional<Property> property = propertyRepository.findById(id);

        if (isAdmin(callerRole)) {
            return property;
        }

        UUID callerId = getCurrentUserId();

        return property.filter(p -> {
            if (callerRole == Role.OWNER) return callerId.equals(p.getOwnerId());
            if (callerRole == Role.TENANT) return callerId.equals(p.getTenantId());
            return false;
        });
    }

    // --- Mutaciones (solo ADMIN+) ---

    @Transactional
    public Property create(Property property) {
        validateIsAdmin();
        return propertyRepository.save(property);
    }

    @Transactional
    public Property update(UUID id, String address, Double squareMeters, Integer rooms, PropertyStatus status) {
        validateIsAdmin();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found: " + id));

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
                .orElseThrow(() -> new RuntimeException("Property not found: " + id));
        property.setActive(false);
        property.setStatus(PropertyStatus.MAINTENANCE);
        propertyRepository.save(property);
    }

    // --- Helpers internos (usados por ContractService) ---

    @Transactional
    public void assignTenant(UUID propertyId, UUID tenantId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found: " + propertyId));
        property.setTenantId(tenantId);
        property.setStatus(PropertyStatus.RENTED);
        propertyRepository.save(property);
    }

    @Transactional
    public void clearTenant(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found: " + propertyId));
        property.setTenantId(null);
        property.setStatus(PropertyStatus.AVAILABLE);
        propertyRepository.save(property);
    }

    // --- RBAC helpers ---

    private boolean isAdmin(Role role) {
        return ROLE_LEVEL.get(role) <= ROLE_LEVEL.get(Role.ADMIN);
    }

    private void validateIsAdmin() {
        Role callerRole = getCurrentUserRole();
        if (!isAdmin(callerRole)) {
            throw new RbacException("Role " + callerRole + " cannot modify properties");
        }
    }

    private Role getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .filter(r -> {
                    try { Role.valueOf(r); return true; } catch (Exception e) { return false; }
                })
                .map(Role::valueOf)
                .min((a, b) -> ROLE_LEVEL.get(a) - ROLE_LEVEL.get(b))
                .orElseThrow(() -> new RbacException("No valid role found in token"));
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new RbacException("Cannot resolve current user ID from token");
        }
    }
}
