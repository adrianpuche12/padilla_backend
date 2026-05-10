package com.padilla.backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketProviderLogDTO {
    private UUID id;
    private UUID ticketId;
    private OffsetDateTime changedAt;
    private UUID changedBy;
    private UUID oldProviderId;
    private UUID newProviderId;
}
