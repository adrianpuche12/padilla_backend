package com.padilla.backend.service.notification;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.TicketStatus;

import java.util.UUID;

/** PRO-060: eventos de dominio de tickets (decision D6: automatizaciones core con eventos de Spring). */
public final class TicketEvents {

    private TicketEvents() {
    }

    public record Created(Ticket ticket) {}

    public record StatusChanged(Ticket ticket, TicketStatus from, TicketStatus to) {}

    public record ProviderAssigned(Ticket ticket, UUID oldProviderId) {}

    public record CommentAdded(Ticket ticket, UUID authorId, boolean internal) {}

    public record Scheduled(Ticket ticket) {}
}
