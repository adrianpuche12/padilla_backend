package com.padilla.backend.controller;

import com.padilla.backend.dto.property.CreatePropertyRequest;
import com.padilla.backend.dto.property.PropertyDTO;
import com.padilla.backend.dto.property.UpdatePropertyRequest;
import com.padilla.backend.entity.Property;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.service.property.PropertyService;
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
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT')")
    public ResponseEntity<List<PropertyDTO>> getProperties(
            @RequestParam(required = false) PropertyStatus status) {
        List<Property> properties = propertyService.findAll(status);
        return ResponseEntity.ok(properties.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT')")
    public ResponseEntity<PropertyDTO> getProperty(@PathVariable UUID id) {
        return propertyService.findById(id)
                .map(p -> ResponseEntity.ok(toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<PropertyDTO> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = new Property();
        property.setAddress(request.getAddress());
        property.setType(request.getType());
        property.setSquareMeters(request.getSquareMeters());
        property.setRooms(request.getRooms());
        property.setOwnerId(request.getOwnerId());
        property.setStatus(PropertyStatus.AVAILABLE);

        Property created = propertyService.create(property);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<PropertyDTO> updateProperty(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest request) {

        Property updated = propertyService.update(
                id,
                request.getAddress(),
                request.getSquareMeters(),
                request.getRooms(),
                request.getStatus()
        );
        return ResponseEntity.ok(toDTO(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deactivateProperty(@PathVariable UUID id) {
        propertyService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    private PropertyDTO toDTO(Property p) {
        return new PropertyDTO(
                p.getId(),
                p.getAddress(),
                p.getType(),
                p.getSquareMeters(),
                p.getRooms(),
                p.getOwnerId(),
                p.getTenantId(),
                p.getStatus(),
                p.isActive(),
                p.getCreatedAt()
        );
    }
}
