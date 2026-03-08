package com.padilla.backend.controller;

import com.padilla.backend.dto.ticket.CreateTicketRequest;
import com.padilla.backend.dto.ticket.TicketCommentDTO;
import com.padilla.backend.dto.ticket.TicketDTO;
import com.padilla.backend.dto.ticket.UpdateTicketRequest;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.TicketComment;
import com.padilla.backend.enums.TicketPriority;
import com.padilla.backend.enums.TicketStatus;
import com.padilla.backend.service.ticket.TicketService;
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
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
    public ResponseEntity<List<TicketDTO>> getTickets(
            @RequestParam(required = false) TicketStatus status) {
        List<Ticket> tickets = ticketService.findAll(status);
        return ResponseEntity.ok(tickets.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
    public ResponseEntity<TicketDTO> getTicket(@PathVariable UUID id) {
        return ticketService.findById(id)
                .map(t -> ResponseEntity.ok(toDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'TENANT', 'PROVIDER')")
    public ResponseEntity<List<TicketCommentDTO>> getComments(@PathVariable UUID id) {
        List<TicketComment> comments = ticketService.findComments(id);
        return ResponseEntity.ok(comments.stream().map(this::toCommentDTO).collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'TENANT')")
    public ResponseEntity<TicketDTO> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setCategory(request.getCategory());
        ticket.setPriority(request.getPriority() != null ? request.getPriority() : TicketPriority.NORMAL);
        ticket.setPropertyId(request.getPropertyId());

        Ticket created = ticketService.create(ticket);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'PROVIDER')")
    public ResponseEntity<TicketDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketRequest request) {
        Ticket updated = ticketService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(toDTO(updated));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<TicketDTO> assignProvider(
            @PathVariable UUID id,
            @RequestParam UUID providerId) {
        Ticket updated = ticketService.assignProvider(id, providerId);
        return ResponseEntity.ok(toDTO(updated));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<TicketDTO> rejectTicket(
            @PathVariable UUID id,
            @RequestParam String reason) {
        Ticket updated = ticketService.reject(id, reason);
        return ResponseEntity.ok(toDTO(updated));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'TENANT', 'PROVIDER')")
    public ResponseEntity<TicketCommentDTO> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody TicketCommentDTO request) {
        TicketComment comment = ticketService.addComment(id, request.getContent(), request.isInternal());
        return ResponseEntity.status(HttpStatus.CREATED).body(toCommentDTO(comment));
    }

    private TicketDTO toDTO(Ticket t) {
        return new TicketDTO(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getCategory(),
                t.getStatus(),
                t.getPriority(),
                t.getPropertyId(),
                t.getTenantId(),
                t.getProviderId(),
                t.getRejectionReason(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getClosedAt()
        );
    }

    private TicketCommentDTO toCommentDTO(TicketComment c) {
        return new TicketCommentDTO(
                c.getId(),
                c.getTicketId(),
                c.getAuthorId(),
                c.getContent(),
                c.isInternal(),
                c.getCreatedAt()
        );
    }
}
