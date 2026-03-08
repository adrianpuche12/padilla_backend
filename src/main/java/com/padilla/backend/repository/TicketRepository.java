package com.padilla.backend.repository;

import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    List<Ticket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Ticket> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TicketStatus status);

    List<Ticket> findByProviderIdOrderByCreatedAtDesc(UUID providerId);

    List<Ticket> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

    long countByStatus(TicketStatus status);
}
