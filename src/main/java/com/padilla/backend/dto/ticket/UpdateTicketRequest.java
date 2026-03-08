package com.padilla.backend.dto.ticket;

import com.padilla.backend.enums.TicketPriority;
import com.padilla.backend.enums.TicketStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateTicketRequest {

    private TicketStatus status;

    private TicketPriority priority;

    private UUID providerId;

    private String rejectionReason;
}
