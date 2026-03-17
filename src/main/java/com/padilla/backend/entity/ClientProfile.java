package com.padilla.backend.entity;

import com.padilla.backend.enums.PersonaType;
import com.padilla.backend.enums.ProviderSpecialty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_profiles", schema = "padilla_dev")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "legacy_id")
    private Integer legacyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "persona_type", nullable = false, length = 10)
    private PersonaType personaType = PersonaType.FISICA;

    @Column(name = "document_type", length = 20)
    private String documentType;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "cuit_cuil", length = 20)
    private String cuitCuil;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "iva_condition", length = 50)
    private String ivaCondition;

    // Solo para PROVIDER — especialidad del prestador de servicios
    @Enumerated(EnumType.STRING)
    @Column(name = "specialty", length = 30)
    private ProviderSpecialty specialty;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
