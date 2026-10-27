package com.padilla.backend.repository;

import com.padilla.backend.entity.ReservaGarante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaGaranteRepository extends JpaRepository<ReservaGarante, UUID> {
    List<ReservaGarante> findByReservaId(UUID reservaId);
    void deleteByReservaId(UUID reservaId);
}
