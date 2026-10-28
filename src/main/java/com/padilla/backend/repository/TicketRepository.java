package com.padilla.backend.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.padilla.backend.entity.Ticket;
import com.padilla.backend.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    List<Ticket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Ticket> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TicketStatus status);

    List<Ticket> findByProviderIdOrderByCreatedAtDesc(UUID providerId);

    List<Ticket> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

    List<Ticket> findByPropertyIdInOrderByCreatedAtDesc(Collection<UUID> propertyIds);

    long countByStatus(TicketStatus status);
}
