package com.padilla.backend.repository;

import com.padilla.backend.entity.ContractPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractPeriodRepository extends JpaRepository<ContractPeriod, UUID> {

    List<ContractPeriod> findByContractIdOrderByPeriodFromAsc(UUID contractId);
}
