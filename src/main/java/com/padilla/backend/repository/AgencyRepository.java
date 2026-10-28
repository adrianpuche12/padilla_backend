package com.padilla.backend.repository;

import com.padilla.backend.entity.Agency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface AgencyRepository extends JpaRepository<Agency, UUID> {

    Optional<Agency> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
