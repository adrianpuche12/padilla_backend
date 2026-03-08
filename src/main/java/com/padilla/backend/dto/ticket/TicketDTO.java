package com.padilla.backend.dto.ticket;

import com.padilla.backend.enums.TicketCategory;
import com.padilla.backend.enums.TicketPriority;
import com.padilla.backend.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {
    private UUID id;
    private String title;
    private String description;
    private TicketCategory category;
    private TicketStatus status;
    private TicketPriority priority;
    private UUID propertyId;
    private UUID tenantId;
    private UUID providerId;
    private String rejectionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime closedAt;
}
