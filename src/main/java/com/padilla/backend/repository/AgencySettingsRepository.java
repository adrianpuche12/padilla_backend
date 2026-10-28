package com.padilla.backend.repository;

import com.padilla.backend.entity.AgencySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// PRO-021: toda consulta corre en una transaccion, asi el transaction manager fija la agencia (RLS)
@Transactional(readOnly = true)
public interface AgencySettingsRepository extends JpaRepository<AgencySettings, UUID> {
}
