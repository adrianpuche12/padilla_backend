package com.padilla.backend.repository;

import java.util.List;
import java.util.Collection;
import com.padilla.backend.entity.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {

    Optional<ClientProfile> findByUserId(UUID userId);

    /** PRO-040: perfiles de una pagina en un solo query (antes uno por fila). */
    List<ClientProfile> findByUserIdIn(Collection<UUID> userIds);
}
