package com.padilla.backend.service.ticket;


import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.TicketComment;
import com.padilla.backend.entity.TicketProviderLog;
import com.padilla.backend.enums.Role;
import com.padilla.backend.enums.TicketStatus;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.TicketCommentRepository;
import com.padilla.backend.repository.TicketProviderLogRepository;
import com.padilla.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketProviderLogRepository ticketProviderLogRepository;

    // --- Consultas ---

    public List<Ticket> findAll(TicketStatus status) {
        Role callerRole = CurrentUser.role();

        if (isAdmin(callerRole)) {
            if (status != null) return ticketRepository.findByStatusOrderByCreatedAtDesc(status);
            return ticketRepository.findAllByOrderByCreatedAtDesc();
        }

        UUID callerId = CurrentUser.id();

        if (callerRole == Role.TENANT) {
            if (status != null) return ticketRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(callerId, status);
            return ticketRepository.findByTenantIdOrderByCreatedAtDesc(callerId);
        }

        if (callerRole == Role.PROVIDER) {
            return ticketRepository.findByProviderIdOrderByCreatedAtDesc(callerId);
        }

        if (callerRole == Role.OWNER) {
            return ticketRepository.findByPropertyIdOrderByCreatedAtDesc(callerId);
        }

        throw new RbacException("Role " + callerRole + " has no access to tickets");
    }

    public Optional<Ticket> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        Optional<Ticket> ticket = ticketRepository.findById(id);

        if (isAdmin(callerRole)) return ticket;

        UUID callerId = CurrentUser.id();

        return ticket.filter(t -> {
            if (callerRole == Role.TENANT)   return callerId.equals(t.getTenantId());
            if (callerRole == Role.PROVIDER) return callerId.equals(t.getProviderId());
            if (callerRole == Role.OWNER)    return callerId.equals(t.getPropertyId());
            return false;
        });
    }

    public List<TicketComment> findComments(UUID ticketId) {
        findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));

        Role callerRole = CurrentUser.role();
        if (isAdmin(callerRole) || callerRole == Role.PROVIDER) {
            return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        }
        return ticketCommentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticketId);
    }

    // --- Mutaciones ---

    @Transactional
    public Ticket create(Ticket ticket) {
        Role callerRole = CurrentUser.role();
        if (callerRole != Role.TENANT && !isAdmin(callerRole)) {
            throw new RbacException("Only TENANT can open tickets");
        }

        if (callerRole == Role.TENANT) {
            ticket.setTenantId(CurrentUser.id());
        }

        ticket.setStatus(TicketStatus.ABIERTA);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateStatus(UUID id, TicketStatus newStatus) {
        Role callerRole = CurrentUser.role();
        UUID callerId = CurrentUser.id();

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + id));

        if (callerRole == Role.PROVIDER) {
            if (!callerId.equals(ticket.getProviderId())) {
                throw new RbacException("PROVIDER can only update their own assigned tickets");
            }
            if (newStatus != TicketStatus.EN_PROCESO && newStatus != TicketStatus.RESUELTA) {
                throw new RbacException("PROVIDER can only set EN_PROCESO or RESUELTA");
            }
        } else if (!isAdmin(callerRole)) {
            throw new RbacException("Role " + callerRole + " cannot update ticket status");
        }

        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.CERRADA || newStatus == TicketStatus.RESUELTA) {
            ticket.setClosedAt(OffsetDateTime.now());
        }
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket assignProvider(UUID ticketId, UUID providerId) {
        validateIsAdmin();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));

        TicketProviderLog log = new TicketProviderLog();
        log.setTicketId(ticketId);
        log.setChangedBy(CurrentUser.id());
        log.setOldProviderId(ticket.getProviderId());
        log.setNewProviderId(providerId);
        ticketProviderLogRepository.save(log);

        ticket.setProviderId(providerId);
        ticket.setStatus(TicketStatus.EN_PROCESO);
        return ticketRepository.save(ticket);
    }

    public List<TicketProviderLog> getProviderHistory(UUID ticketId) {
        validateIsAdmin();
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        return ticketProviderLogRepository.findByTicketIdOrderByChangedAtDesc(ticketId);
    }

    @Transactional
    public Ticket reject(UUID ticketId, String reason) {
        validateIsAdmin();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));

        ticket.setStatus(TicketStatus.RECHAZADA);
        ticket.setRejectionReason(reason);
        ticket.setClosedAt(OffsetDateTime.now());
        return ticketRepository.save(ticket);
    }

    @Transactional
    public TicketComment addComment(UUID ticketId, String content, boolean internal) {
        Role callerRole = CurrentUser.role();

        findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));

        if (callerRole == Role.TENANT && internal) {
            throw new RbacException("TENANT cannot add internal comments");
        }
        if (callerRole == Role.OWNER) {
            throw new RbacException("OWNER cannot comment on tickets");
        }

        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(CurrentUser.id());
        comment.setContent(content);
        comment.setInternal(internal);
        return ticketCommentRepository.save(comment);
    }

    // --- RBAC helpers ---

    private boolean isAdmin(Role role) {
        return role.level() <= Role.ADMIN.level();
    }

    private void validateIsAdmin() {
        Role callerRole = CurrentUser.role();
        if (!isAdmin(callerRole)) {
            throw new RbacException("Role " + callerRole + " cannot perform this action");
        }
    }


}
