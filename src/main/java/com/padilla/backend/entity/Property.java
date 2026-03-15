package com.padilla.backend.entity;

import com.padilla.backend.enums.PropertyRentalStatus;
import com.padilla.backend.enums.PropertySaleStatus;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.PropertyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "properties", schema = "padilla_dev")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "address", nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PropertyType type;

    @Column(name = "square_meters")
    private Double squareMeters;

    @Column(name = "rooms")
    private Integer rooms;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PropertyStatus status = PropertyStatus.AVAILABLE;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // --- Campos Sprint 7 (legacy + datos Excel) ---

    @Column(name = "legacy_id")
    private Integer legacyId;

    @Column(name = "street", length = 150)
    private String street;

    @Column(name = "street_number", length = 20)
    private String streetNumber;

    @Column(name = "floor", length = 10)
    private String floor;

    @Column(name = "apartment", length = 10)
    private String apartment;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "province", length = 100)
    private String province;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_status", length = 30)
    private PropertyRentalStatus rentalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_status", length = 30)
    private PropertySaleStatus saleStatus;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "rental_producer", length = 150)
    private String rentalProducer;

    @Column(name = "sale_producer", length = 150)
    private String saleProducer;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
