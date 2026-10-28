package com.padilla.backend.controller;

import com.padilla.backend.service.listing.Listings;
import java.util.Set;
import org.springframework.data.domain.Sort;
import com.padilla.backend.util.Paging;
import com.padilla.backend.dto.PageResponse;

import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.dto.ticket.CreateTicketRequest;
import com.padilla.backend.dto.ticket.TicketCommentDTO;
import com.padilla.backend.dto.ticket.TicketDTO;
import com.padilla.backend.dto.ticket.TicketProviderLogDTO;
import com.padilla.backend.dto.ticket.UpdateTicketRequest;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.TicketComment;
import com.padilla.backend.entity.TicketProviderLog;
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
    public ResponseEntity<?> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) com.padilla.backend.enums.TicketPriority priority,
            @RequestParam(required = false) com.padilla.backend.enums.TicketCategory category,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q) {
        // PRO-040 / PRO-051: con ?page= devuelve una pagina filtrada (server-side); sin page, como antes
        if (page != null) {
            return ResponseEntity.ok(PageResponse.of(ticketService.page(
                    new Listings.TicketFilter(status, priority, category, propertyId, providerId, from, to, q),
                    Paging.request(page, size, sort, Set.of("createdAt", "ticketNumber", "priority", "status", "updatedAt"),
                            Sort.by(Sort.Direction.DESC, "createdAt"))),
                    this::toDTO));
        }
        List<Ticket> tickets = ticketService.findAll(status);
        return ResponseEntity.ok(tickets.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
    public ResponseEntity<TicketDTO> getTicket(@PathVariable UUID id) {
        return ticketService.findById(id)
                .map(t -> ResponseEntity.ok(toDTO(t)))
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado"));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
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
        // PRO-051: metadata del modo de carga (el backend no depende del modo: mismo POST)
        if (request.getModoCarga() != null) ticket.setModoCarga(request.getModoCarga().name());
        ticket.setTiempoCargaSegundos(request.getTiempoCargaSegundos());
        ticket.setDispositivo(request.getDispositivo());

        Ticket created = ticketService.create(ticket, request.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'TENANT', 'PROVIDER')")
    public ResponseEntity<TicketDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketRequest request) {
        Ticket updated = ticketService.updateStatus(id, request.getStatus(), request.getRejectionReason());
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

    /**
     * PRO-041: proveedores activos cuya especialidad resuelve la categoria del ticket
     * (para OTRO, todos los activos). Los que ya tienen menos tickets abiertos van primero.
     */
    @GetMapping("/{id}/suggested-providers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<java.util.Map<String, Object>>> suggestedProviders(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.suggestedProviders(id));
    }

    public record ScheduleRequest(java.time.OffsetDateTime scheduledAt) {}

    /** PRO-054: fecha de visita acordada (personal o proveedor asignado). Avisa al inquilino. */
    @PatchMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'PROVIDER')")
    public ResponseEntity<TicketDTO> schedule(@PathVariable UUID id, @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(toDTO(ticketService.schedule(id, req.scheduledAt())));
    }

    public record CostRequest(java.math.BigDecimal costoFinal) {}

    /** PRO-054: costo final del trabajo. */
    @PatchMapping("/{id}/cost")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'PROVIDER')")
    public ResponseEntity<TicketDTO> cost(@PathVariable UUID id, @RequestBody CostRequest req) {
        return ResponseEntity.ok(toDTO(ticketService.setFinalCost(id, req.costoFinal())));
    }

    @GetMapping("/{id}/provider-history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<TicketProviderLogDTO>> getProviderHistory(@PathVariable UUID id) {
        List<TicketProviderLog> logs = ticketService.getProviderHistory(id);
        return ResponseEntity.ok(logs.stream().map(this::toProviderLogDTO).collect(Collectors.toList()));
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
                t.getTicketNumber(),
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
                t.getClosedAt(),
                t.getModoCarga(),
                t.getTiempoCargaSegundos(),
                t.getDispositivo(),
                t.getScheduledAt(),
                t.getCostoFinal()
        );
    }

    private TicketProviderLogDTO toProviderLogDTO(TicketProviderLog l) {
        return new TicketProviderLogDTO(
                l.getId(),
                l.getTicketId(),
                l.getChangedAt(),
                l.getChangedBy(),
                l.getOldProviderId(),
                l.getNewProviderId()
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
