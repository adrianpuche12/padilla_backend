package com.padilla.backend.entity;

import com.padilla.backend.enums.TicketCategory;
import com.padilla.backend.enums.TicketPriority;
import com.padilla.backend.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TicketStatus status = TicketStatus.ABIERTA;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TicketPriority priority = TicketPriority.NORMAL;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "ticket_number", insertable = false, updatable = false)
    private Integer ticketNumber;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** PRO-051: como se cargo el ticket (FORMULARIO, VOZ_GUIADA, ASISTENTE_IA). */
    @Column(name = "modo_carga", nullable = false)
    private String modoCarga = "FORMULARIO";

    @Column(name = "tiempo_carga_segundos")
    private Integer tiempoCargaSegundos;

    /** iOS, Android o Web. */
    @Column(name = "dispositivo")
    private String dispositivo;

    /** PRO-054: fecha de visita acordada (siempre coordinada por la plataforma). */
    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    /** PRO-054: costo final del trabajo (para analytics). */
    @Column(name = "costo_final")
    private java.math.BigDecimal costoFinal;
}
