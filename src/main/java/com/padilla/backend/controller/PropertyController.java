package com.padilla.backend.controller;

import com.padilla.backend.service.listing.Listings;
import java.util.Set;
import org.springframework.data.domain.Sort;
import com.padilla.backend.util.Paging;
import com.padilla.backend.dto.PageResponse;

import com.padilla.backend.exception.NotFoundException;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT')")
public class PropertyController {

    private final com.padilla.backend.repository.TicketRepository ticketRepository;
    private final com.padilla.backend.repository.ContractRepository contractRepository;

    private final com.padilla.backend.repository.UserRepository userRepository;

    private final com.padilla.backend.repository.PropertyRepository propertyRepository;

    private final PropertyService propertyService;

    @GetMapping
    public ResponseEntity<?> getProperties(
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        // PRO-040: con ?page= devuelve una pagina (server-side); sin page, la lista completa como antes
        if (page != null) {
            return ResponseEntity.ok(PageResponse.of(propertyRepository.findAll(
                    Listings.properties(status, active, ownerId, q),
                    Paging.request(page, size, sort, Set.of("address", "city", "createdAt", "status"), Sort.by("address"))),
                    this::toDTO));
        }
        List<Property> properties = propertyService.findAll(status);
        return ResponseEntity.ok(properties.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyDTO> getProperty(@PathVariable UUID id) {
        return propertyService.findById(id)
                .map(p -> ResponseEntity.ok(toDTO(p)))
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada"));
    }

    /**
     * PRO-047: historial de la propiedad: contratos (con inquilino) y tickets, del mas reciente al mas viejo.
     * Mismo acceso que ver la propiedad.
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER')")
    public java.util.Map<String, Object> history(@PathVariable UUID id) {
        propertyService.findById(id).orElseThrow(() -> new NotFoundException("Propiedad no encontrada"));
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("contracts", contractRepository.findByPropertyId(id).stream()
                .sorted(java.util.Comparator.comparing(com.padilla.backend.entity.Contract::getStartDate).reversed())
                .map(c -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("status", c.getStatus());
                    m.put("startDate", c.getStartDate());
                    m.put("endDate", c.getEndDate());
                    m.put("monthlyAmount", c.getMonthlyAmount());
                    m.put("tenantId", c.getTenantId());
                    m.put("tenantName", userRepository.findById(c.getTenantId()).map(u -> u.getName()).orElse(null));
                    return m;
                }).toList());
        r.put("tickets", ticketRepository.findByPropertyIdOrderByCreatedAtDesc(id).stream().map(t -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("ticketNumber", t.getTicketNumber());
            m.put("title", t.getTitle());
            m.put("category", t.getCategory());
            m.put("status", t.getStatus());
            m.put("createdAt", t.getCreatedAt());
            m.put("closedAt", t.getClosedAt());
            return m;
        }).toList());
        return r;
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
        property.setLegacyId(request.getLegacyId());
        property.setStreet(request.getStreet());
        property.setStreetNumber(request.getStreetNumber());
        property.setFloor(request.getFloor());
        property.setApartment(request.getApartment());
        property.setCity(request.getCity());
        property.setProvince(request.getProvince());
        property.setRentalStatus(request.getRentalStatus());
        property.setSaleStatus(request.getSaleStatus());
        property.setEntryDate(request.getEntryDate());
        property.setRentalProducer(request.getRentalProducer());
        property.setSaleProducer(request.getSaleProducer());

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

    /** PRO-043: forma recomendada. DELETE /{id} queda como alias (deprecado) por compatibilidad. */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deactivatePropertyPatch(@PathVariable UUID id) {
        propertyService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    @Deprecated
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deactivateProperty(@PathVariable UUID id) {
        propertyService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Void> reactivateProperty(@PathVariable UUID id) {
        propertyService.reactivate(id);
        return ResponseEntity.ok().build();
    }

    private PropertyDTO toDTO(Property p) {
        PropertyDTO dto = new PropertyDTO();
        dto.setId(p.getId());
        dto.setAddress(p.getAddress());
        dto.setType(p.getType());
        dto.setSquareMeters(p.getSquareMeters());
        dto.setRooms(p.getRooms());
        dto.setOwnerId(p.getOwnerId());
        dto.setTenantId(p.getTenantId());
        dto.setStatus(p.getStatus());
        dto.setActive(p.isActive());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setLegacyId(p.getLegacyId());
        dto.setStreet(p.getStreet());
        dto.setStreetNumber(p.getStreetNumber());
        dto.setFloor(p.getFloor());
        dto.setApartment(p.getApartment());
        dto.setCity(p.getCity());
        dto.setProvince(p.getProvince());
        dto.setRentalStatus(p.getRentalStatus());
        dto.setSaleStatus(p.getSaleStatus());
        dto.setEntryDate(p.getEntryDate());
        dto.setRentalProducer(p.getRentalProducer());
        dto.setSaleProducer(p.getSaleProducer());
        return dto;
    }
}
