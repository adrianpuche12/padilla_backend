package com.padilla.backend.repository;

import org.springframework.transaction.annotation.Transactional;

import com.padilla.backend.entity.TicketProviderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface TicketProviderLogRepository extends JpaRepository<TicketProviderLog, UUID> {
    List<TicketProviderLog> findByTicketIdOrderByChangedAtDesc(UUID ticketId);
}
