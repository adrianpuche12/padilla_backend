package com.padilla.backend.service.listing;

import com.padilla.backend.entity.ClientProfile;
import com.padilla.backend.entity.Contract;
import com.padilla.backend.entity.Property;
import com.padilla.backend.entity.Reserva;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.entity.User;
import com.padilla.backend.enums.ContractStatus;
import com.padilla.backend.enums.PropertyStatus;
import com.padilla.backend.enums.Role;
import com.padilla.backend.enums.TicketCategory;
import com.padilla.backend.enums.TicketPriority;
import com.padilla.backend.enums.TicketStatus;
import com.padilla.backend.exception.RbacException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.util.Paging;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * PRO-040 / PRO-051: filtros de los listados paginados como Specifications de Spring Data.
 * Cada uno aplica la misma visibilidad por rol que el listado sin paginar del modulo, asi la
 * paginacion nunca muestra mas de lo que el usuario puede ver. La agencia la filtra RLS.
 */
public final class Listings {

    private Listings() {
    }

    private static <T> Specification<T> and(List<Specification<T>> parts) {
        Specification<T> result = Specification.where(null);
        for (Specification<T> p : parts) {
            if (p != null) result = result.and(p);
        }
        return result;
    }

    // --- Usuarios de sistema y clientes ---

    public static Specification<User> users(Collection<Role> roles, Role role, Boolean active, String q) {
        List<Specification<User>> parts = new ArrayList<>();
        parts.add((r, cq, cb) -> r.get("role").in(role != null ? List.of(role) : roles));
        if (active != null) parts.add((r, cq, cb) -> cb.equal(r.get("active"), active));
        if (Paging.hasText(q)) {
            parts.add((r, cq, cb) -> {
                Subquery<UUID> doc = cq.subquery(UUID.class);
                Root<ClientProfile> p = doc.from(ClientProfile.class);
                doc.select(p.get("userId")).where(cb.or(
                        Paging.contains(cb, p.get("documentNumber"), q),
                        Paging.contains(cb, p.get("cuitCuil"), q)));
                return cb.or(Paging.contains(cb, r.get("name"), q), Paging.contains(cb, r.get("email"), q),
                        r.get("id").in(doc));
            });
        }
        return and(parts);
    }

    // --- Propiedades ---

    public static Specification<Property> properties(PropertyStatus status, Boolean active, UUID ownerId, String q) {
        Role role = CurrentUser.role();
        List<Specification<Property>> parts = new ArrayList<>();
        if (!role.isManagement()) {
            UUID me = CurrentUser.id();
            switch (role) {
                case OWNER -> parts.add((r, cq, cb) -> cb.and(cb.equal(r.get("ownerId"), me), cb.isTrue(r.get("active"))));
                case TENANT -> parts.add((r, cq, cb) -> cb.and(cb.equal(r.get("tenantId"), me), cb.isTrue(r.get("active"))));
                default -> throw new RbacException("El rol " + role + " no tiene acceso a propiedades");
            }
        }
        if (status != null) parts.add((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (active != null) parts.add((r, cq, cb) -> cb.equal(r.get("active"), active));
        if (ownerId != null) parts.add((r, cq, cb) -> cb.equal(r.get("ownerId"), ownerId));
        if (Paging.hasText(q)) {
            parts.add((r, cq, cb) -> cb.or(Paging.contains(cb, r.get("address"), q), Paging.contains(cb, r.get("city"), q)));
        }
        return and(parts);
    }

    // --- Contratos ---

    public static Specification<Contract> contracts(ContractStatus status, UUID propertyId, UUID ownerId, UUID tenantId,
                                                    LocalDate endsBefore) {
        Role role = CurrentUser.role();
        List<Specification<Contract>> parts = new ArrayList<>();
        if (!role.isManagement()) {
            UUID me = CurrentUser.id();
            switch (role) {
                case OWNER -> parts.add((r, cq, cb) -> cb.and(cb.equal(r.get("ownerId"), me), cb.isTrue(r.get("active"))));
                case TENANT -> parts.add((r, cq, cb) -> cb.and(cb.equal(r.get("tenantId"), me), cb.isTrue(r.get("active"))));
                default -> throw new RbacException("El rol " + role + " no tiene acceso a contratos");
            }
        }
        if (status != null) parts.add((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (propertyId != null) parts.add((r, cq, cb) -> cb.equal(r.get("propertyId"), propertyId));
        if (ownerId != null) parts.add((r, cq, cb) -> cb.equal(r.get("ownerId"), ownerId));
        if (tenantId != null) parts.add((r, cq, cb) -> cb.equal(r.get("tenantId"), tenantId));
        if (endsBefore != null) parts.add((r, cq, cb) -> cb.lessThanOrEqualTo(r.get("endDate"), endsBefore));
        return and(parts);
    }

    // --- Tickets ---

    public record TicketFilter(TicketStatus status, TicketPriority priority, TicketCategory category,
                               UUID propertyId, UUID providerId, LocalDate from, LocalDate to, String q) {}

    public static Specification<Ticket> tickets(TicketFilter f, Collection<UUID> ownedPropertyIds) {
        Role role = CurrentUser.role();
        List<Specification<Ticket>> parts = new ArrayList<>();
        if (!role.isManagement()) {
            UUID me = CurrentUser.id();
            switch (role) {
                case TENANT -> parts.add((r, cq, cb) -> cb.equal(r.get("tenantId"), me));
                case PROVIDER -> parts.add((r, cq, cb) -> cb.equal(r.get("providerId"), me));
                case OWNER -> parts.add((r, cq, cb) -> ownedPropertyIds.isEmpty()
                        ? cb.disjunction() : r.get("propertyId").in(ownedPropertyIds));
                default -> throw new RbacException("El rol " + role + " no tiene acceso a tickets");
            }
        }
        if (f.status() != null) parts.add((r, cq, cb) -> cb.equal(r.get("status"), f.status()));
        if (f.priority() != null) parts.add((r, cq, cb) -> cb.equal(r.get("priority"), f.priority()));
        if (f.category() != null) parts.add((r, cq, cb) -> cb.equal(r.get("category"), f.category()));
        if (f.propertyId() != null) parts.add((r, cq, cb) -> cb.equal(r.get("propertyId"), f.propertyId()));
        if (f.providerId() != null) parts.add((r, cq, cb) -> cb.equal(r.get("providerId"), f.providerId()));
        ZoneId zona = ZoneId.of("America/Argentina/Buenos_Aires");
        if (f.from() != null) {
            OffsetDateTime desde = f.from().atStartOfDay(zona).toOffsetDateTime();
            parts.add((r, cq, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), desde));
        }
        if (f.to() != null) {
            OffsetDateTime hasta = f.to().plusDays(1).atStartOfDay(zona).toOffsetDateTime();
            parts.add((r, cq, cb) -> cb.lessThan(r.get("createdAt"), hasta));
        }
        if (Paging.hasText(f.q())) {
            String q = f.q().trim();
            parts.add((r, cq, cb) -> {
                List<Predicate> or = new ArrayList<>(List.of(
                        Paging.contains(cb, r.get("title"), q), Paging.contains(cb, r.get("description"), q)));
                String numero = q.startsWith("#") ? q.substring(1) : q;
                if (numero.matches("\\d{1,9}")) or.add(cb.equal(r.get("ticketNumber"), Integer.parseInt(numero)));
                return cb.or(or.toArray(Predicate[]::new));
            });
        }
        return and(parts);
    }

    // --- Reservas ---

    public static Specification<Reserva> reservas(String status, String q) {
        Role role = CurrentUser.role();
        List<Specification<Reserva>> parts = new ArrayList<>();
        if (!role.isManagement()) {
            UUID me = CurrentUser.id();
            parts.add((r, cq, cb) -> cb.equal(r.get("operadorId"), me));
        }
        if (status != null) parts.add((r, cq, cb) -> cb.equal(r.get("status"), status));
        if (Paging.hasText(q)) {
            parts.add((r, cq, cb) -> cb.or(Paging.contains(cb, r.get("nombre"), q), Paging.contains(cb, r.get("apellido"), q),
                    Paging.contains(cb, r.get("dni"), q), Paging.contains(cb, r.get("propiedad"), q)));
        }
        return and(parts);
    }
}
