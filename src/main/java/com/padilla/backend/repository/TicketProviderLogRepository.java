package com.padilla.backend.repository;

import com.padilla.backend.entity.TicketProviderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketProviderLogRepository extends JpaRepository<TicketProviderLog, UUID> {
    List<TicketProviderLog> findByTicketIdOrderByChangedAtDesc(UUID ticketId);
}
