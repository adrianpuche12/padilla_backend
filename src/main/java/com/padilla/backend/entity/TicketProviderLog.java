package com.padilla.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_provider_log", schema = "padilla_dev")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketProviderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "old_provider_id")
    private UUID oldProviderId;

    @Column(name = "new_provider_id")
    private UUID newProviderId;

    @PrePersist
    protected void onCreate() {
        this.changedAt = OffsetDateTime.now();
    }
}
