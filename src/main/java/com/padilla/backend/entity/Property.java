package com.padilla.backend.entity;

import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.PropertyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
