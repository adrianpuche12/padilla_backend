package com.padilla.backend.service.ticket;

import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.Property;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.TicketComment;
import com.padilla.backend.entity.TicketProviderLog;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.enums.TicketStatus;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.exception.ConflictException;
import com.padilla.backend.exception.NotFoundException;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.repository.ContractRepository;
import com.padilla.backend.repository.PropertyRepository;
import com.padilla.backend.repository.TicketCommentRepository;
import com.padilla.backend.repository.TicketProviderLogRepository;
import com.padilla.backend.repository.TicketRepository;
import com.padilla.backend.repository.UserRepository;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.TicketEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketProviderLogRepository ticketProviderLogRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ContractRepository contractRepository;
    private final com.padilla.backend.repository.ClientProfileRepository clientProfileRepository;
    // PRO-060: los cambios de los tickets se publican como eventos (notificaciones, decision D6)
    private final org.springframework.context.ApplicationEventPublisher events;

    // --- Consultas ---

    public List<Ticket> findAll(TicketStatus status) {
        Role callerRole = CurrentUser.role();

        if (callerRole.isManagement()) {
            if (status != null) return ticketRepository.findByStatusOrderByCreatedAtDesc(status);
            return ticketRepository.findAllByOrderByCreatedAtDesc();
        }

        UUID callerId = CurrentUser.id();
        List<Ticket> tickets = switch (callerRole) {
            case TENANT -> ticketRepository.findByTenantIdOrderByCreatedAtDesc(callerId);
            case PROVIDER -> ticketRepository.findByProviderIdOrderByCreatedAtDesc(callerId);
            // PRO-015: antes se buscaba por propertyId = id del usuario y el OWNER nunca veia nada
            case OWNER -> ticketRepository.findByPropertyIdInOrderByCreatedAtDesc(ownedPropertyIds(callerId));
            default -> throw new RbacException("El rol " + callerRole + " no tiene acceso a tickets");
        };
        return status == null ? tickets : tickets.stream().filter(t -> t.getStatus() == status).toList();
    }

    /** PRO-040 / PRO-051: pagina de tickets con filtros, con la misma visibilidad por rol que findAll. */
    public org.springframework.data.domain.Page<Ticket> page(com.padilla.backend.service.listing.Listings.TicketFilter filter,
                                                             org.springframework.data.domain.Pageable pageable) {
        List<UUID> owned = CurrentUser.role() == Role.OWNER ? ownedPropertyIds(CurrentUser.id()) : List.of();
        return ticketRepository.findAll(com.padilla.backend.service.listing.Listings.tickets(filter, owned), pageable);
    }

    public Optional<Ticket> findById(UUID id) {
        Role callerRole = CurrentUser.role();
        Optional<Ticket> ticket = ticketRepository.findById(id);

        if (callerRole.isManagement()) return ticket;

        UUID callerId = CurrentUser.id();
        return ticket.filter(t -> switch (callerRole) {
            case TENANT -> callerId.equals(t.getTenantId());
            case PROVIDER -> callerId.equals(t.getProviderId());
            case OWNER -> isOwnerOf(callerId, t.getPropertyId());
            default -> false;
        });
    }

    public List<TicketComment> findComments(UUID ticketId) {
        requireVisible(ticketId);
        Role callerRole = CurrentUser.role();
        if (callerRole.isManagement() || callerRole == Role.PROVIDER) {
            return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        }
        // TENANT y OWNER: solo comentarios no internos
        return ticketCommentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticketId);
    }

    // --- Mutaciones ---

    /**
     * PRO-012: el inquilino abre tickets de su vivienda; el personal de la inmobiliaria puede abrirlos
     * por el inquilino (llamado telefonico, etc.). Si el admin no indica inquilino se toma el del
     * contrato ACTIVE de la propiedad o el inquilino asignado a la propiedad.
     */
    @Transactional
    public Ticket create(Ticket ticket, UUID requestedTenantId) {
        Role callerRole = CurrentUser.role();
        Property property = propertyRepository.findById(ticket.getPropertyId())
                .orElseThrow(() -> new NotFoundException("Propiedad no encontrada: " + ticket.getPropertyId()));

        if (callerRole == Role.TENANT) {
            UUID callerId = CurrentUser.id();
            if (!callerId.equals(resolveTenant(property).orElse(null))) {
                throw new RbacException("Solo podes abrir tickets de la propiedad que alquilas");
            }
            ticket.setTenantId(callerId);
        } else if (callerRole.isManagement()) {
            UUID tenantId = requestedTenantId != null ? requestedTenantId : resolveTenant(property)
                    .orElseThrow(() -> new BadRequestException(
                            "La propiedad no tiene inquilino: indica para que inquilino es el ticket"));
            User tenant = userRepository.findById(tenantId)
                    .orElseThrow(() -> new NotFoundException("Inquilino no encontrado: " + tenantId));
            if (tenant.getRole() != Role.TENANT) {
                throw new BadRequestException("El usuario indicado no es un inquilino");
            }
            ticket.setTenantId(tenantId);
        } else {
            throw new RbacException("El rol " + callerRole + " no puede abrir tickets");
        }

        ticket.setStatus(TicketStatus.ABIERTA);
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        events.publishEvent(new TicketEvents.Created(saved));
        return saved;
    }

    /** PRO-012: cambio de estado con maquina de estados (TicketStatus#canTransitionTo). */
    @Transactional
    public Ticket updateStatus(UUID id, TicketStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new BadRequestException("El estado es obligatorio");
        }
        Role callerRole = CurrentUser.role();
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + id));

        if (callerRole == Role.PROVIDER) {
            if (!CurrentUser.id().equals(ticket.getProviderId())) {
                throw new RbacException("Solo podes actualizar los tickets que tenes asignados");
            }
            if (newStatus != TicketStatus.EN_PROCESO && newStatus != TicketStatus.RESUELTA) {
                throw new RbacException("El proveedor solo puede pasar el ticket a EN_PROCESO o RESUELTA");
            }
        } else if (callerRole == Role.TENANT) {
            // El inquilino solo puede cancelar su propio ticket mientras no fue tomado
            if (!CurrentUser.id().equals(ticket.getTenantId()) || newStatus != TicketStatus.CANCELADA
                    || ticket.getStatus() != TicketStatus.ABIERTA) {
                throw new RbacException("Solo podes cancelar tus tickets mientras estan abiertos");
            }
        } else if (!callerRole.isManagement()) {
            throw new RbacException("El rol " + callerRole + " no puede cambiar el estado de un ticket");
        }

        if (newStatus == TicketStatus.RECHAZADA) {
            return reject(id, reason);
        }
        TicketStatus from = ticket.getStatus();
        transition(ticket, newStatus);
        if (newStatus == TicketStatus.CANCELADA) {
            ticket.setRejectionReason(reason);
        }
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(new TicketEvents.StatusChanged(saved, from, newStatus));
        return saved;
    }

    /**
     * PRO-011: el proveedor tiene que existir, ser PROVIDER y estar activo (antes cualquier UUID
     * entraba y fallaba la FK con 500). changed_by sale del usuario actual, que existe en users
     * gracias a la provision JIT (PRO-005).
     */
    @Transactional
    public Ticket assignProvider(UUID ticketId, UUID providerId) {
        CurrentUser.requireManagement();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("No se puede asignar un proveedor a un ticket " + ticket.getStatus());
        }
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado: " + providerId));
        if (provider.getRole() != Role.PROVIDER) {
            throw new BadRequestException("El usuario indicado no es un proveedor");
        }
        if (!provider.isActive()) {
            throw new BadRequestException("El proveedor esta desactivado");
        }
        if (providerId.equals(ticket.getProviderId())) {
            return ticket;
        }

        TicketProviderLog log = new TicketProviderLog();
        log.setTicketId(ticketId);
        log.setChangedBy(CurrentUser.id());
        log.setOldProviderId(ticket.getProviderId());
        log.setNewProviderId(providerId);
        ticketProviderLogRepository.save(log);

        UUID oldProvider = ticket.getProviderId();
        TicketStatus from = ticket.getStatus();
        ticket.setProviderId(providerId);
        if (ticket.getStatus() == TicketStatus.ABIERTA) {
            transition(ticket, TicketStatus.EN_PROCESO);
        }
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(new TicketEvents.ProviderAssigned(saved, oldProvider));
        if (from != saved.getStatus()) {
            events.publishEvent(new TicketEvents.StatusChanged(saved, from, saved.getStatus()));
        }
        return saved;
    }

    /** PRO-041: proveedores sugeridos segun la categoria del ticket, ordenados por carga de trabajo. */
    public List<java.util.Map<String, Object>> suggestedProviders(UUID ticketId) {
        CurrentUser.requireManagement();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        var especialidad = ticket.getCategory().specialty();
        return userRepository.findByRole(Role.PROVIDER).stream()
                .filter(User::isActive)
                .map(u -> {
                    var perfil = clientProfileRepository.findByUserId(u.getId()).orElse(null);
                    var esp = perfil == null ? null : perfil.getSpecialty();
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("name", u.getName());
                    m.put("specialty", esp);
                    m.put("openTickets", ticketRepository.countByProviderIdAndStatusIn(u.getId(),
                            List.of(TicketStatus.ABIERTA, TicketStatus.EN_PROCESO)));
                    m.put("matches", especialidad == null || especialidad == esp);
                    return m;
                })
                .filter(m -> (Boolean) m.get("matches"))
                .sorted(java.util.Comparator.comparingLong(m -> (Long) m.get("openTickets")))
                .toList();
    }

    /** PRO-054: fecha de visita. La fija el personal o el proveedor asignado; el ticket no puede estar terminado. */
    @Transactional
    public Ticket schedule(UUID ticketId, java.time.OffsetDateTime when) {
        Ticket ticket = requireEditableByStaffOrProvider(ticketId);
        if (when == null || when.isBefore(java.time.OffsetDateTime.now().minusHours(1))) {
            throw new BadRequestException("La fecha de visita tiene que ser futura");
        }
        ticket.setScheduledAt(when);
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(new TicketEvents.Scheduled(saved));
        return saved;
    }

    /** PRO-054: costo final del trabajo (para analytics y reportes de costos). */
    @Transactional
    public Ticket setFinalCost(UUID ticketId, java.math.BigDecimal costo) {
        if (costo == null || costo.signum() < 0) {
            throw new BadRequestException("El costo final tiene que ser 0 o mayor");
        }
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        if (!CurrentUser.isManagement() && !(CurrentUser.role() == Role.PROVIDER && CurrentUser.id().equals(ticket.getProviderId()))) {
            throw new RbacException("Solo la inmobiliaria o el proveedor asignado cargan el costo final");
        }
        ticket.setCostoFinal(costo);
        return ticketRepository.save(ticket);
    }

    private Ticket requireEditableByStaffOrProvider(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        if (!CurrentUser.isManagement() && !(CurrentUser.role() == Role.PROVIDER && CurrentUser.id().equals(ticket.getProviderId()))) {
            throw new RbacException("Solo la inmobiliaria o el proveedor asignado pueden hacer esto");
        }
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("El ticket esta " + ticket.getStatus());
        }
        return ticket;
    }

    public List<TicketProviderLog> getProviderHistory(UUID ticketId) {
        CurrentUser.requireManagement();
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        return ticketProviderLogRepository.findByTicketIdOrderByChangedAtDesc(ticketId);
    }

    @Transactional
    public Ticket reject(UUID ticketId, String reason) {
        CurrentUser.requireManagement();
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("El motivo del rechazo es obligatorio");
        }
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
        TicketStatus from = ticket.getStatus();
        transition(ticket, TicketStatus.RECHAZADA);
        ticket.setRejectionReason(reason);
        Ticket saved = ticketRepository.save(ticket);
        events.publishEvent(new TicketEvents.StatusChanged(saved, from, TicketStatus.RECHAZADA));
        return saved;
    }

    @Transactional
    public TicketComment addComment(UUID ticketId, String content, boolean internal) {
        Role callerRole = CurrentUser.role();
        requireVisible(ticketId);

        if (callerRole == Role.OWNER) {
            throw new RbacException("El propietario no puede comentar tickets");
        }
        if (internal && !callerRole.isManagement()) {
            throw new RbacException("Solo la inmobiliaria puede dejar comentarios internos");
        }

        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(CurrentUser.id());
        comment.setContent(content);
        comment.setInternal(internal);
        TicketComment saved = ticketCommentRepository.save(comment);
        ticketRepository.findById(ticketId).ifPresent(t ->
                events.publishEvent(new TicketEvents.CommentAdded(t, saved.getAuthorId(), internal)));
        return saved;
    }

    // --- Helpers ---

    private static void transition(Ticket ticket, TicketStatus target) {
        TicketStatus current = ticket.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new ConflictException("No se puede pasar un ticket de " + current + " a " + target
                    + ". Estados posibles: " + current.nextStates());
        }
        ticket.setStatus(target);
        if (target.isFinal()) {
            ticket.setClosedAt(OffsetDateTime.now());
        } else if (current.isFinal() || current == TicketStatus.RESUELTA) {
            ticket.setClosedAt(null);
        }
    }

    private void requireVisible(UUID ticketId) {
        findById(ticketId).orElseThrow(() -> new NotFoundException("Ticket no encontrado: " + ticketId));
    }

    private Optional<UUID> resolveTenant(Property property) {
        return contractRepository.findByPropertyIdAndStatus(property.getId(), ContractStatus.ACTIVE)
                .map(Contract::getTenantId)
                .or(() -> Optional.ofNullable(property.getTenantId()));
    }

    private List<UUID> ownedPropertyIds(UUID ownerId) {
        return propertyRepository.findByOwnerId(ownerId).stream().map(Property::getId).toList();
    }

    private boolean isOwnerOf(UUID ownerId, UUID propertyId) {
        return propertyRepository.findById(propertyId).map(p -> ownerId.equals(p.getOwnerId())).orElse(false);
    }
}
