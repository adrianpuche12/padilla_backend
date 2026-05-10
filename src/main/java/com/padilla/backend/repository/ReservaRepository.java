package com.padilla.backend.repository;

import com.padilla.backend.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
    List<Reserva> findAllByOrderByCreatedAtDesc();
    List<Reserva> findByOperadorIdOrderByCreatedAtDesc(UUID operadorId);
    List<Reserva> findByStatusOrderByCreatedAtDesc(String status);
}
