package com.padilla.backend.repository;

import com.padilla.backend.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
    List<Reserva> findAllByOrderByCreatedAtDesc();
    List<Reserva> findByOperadorIdOrderByCreatedAtDesc(UUID operadorId);
    List<Reserva> findByStatusOrderByCreatedAtDesc(String status);
}
