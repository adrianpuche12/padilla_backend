package com.padilla.backend.service.jobs;

import com.padilla.backend.entity.Agency;
import com.padilla.backend.repository.AgencyRepository;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.RlsBypass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * PRO-044: los jobs programados no tienen request (ni agencia). Este runner recorre las agencias
 * activas y ejecuta la tarea dentro de una transaccion con la agencia fijada, asi RLS aplica igual
 * que en un request. Un error en una agencia no corta las demas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgencyJobRunner {

    private final AgencyRepository agencyRepository;
    private final RlsBypass rlsBypass;
    private final PlatformTransactionManager transactionManager;

    public void forEachAgency(String job, Consumer<UUID> task) {
        List<Agency> agencias = rlsBypass.call(agencyRepository::findAll).stream().filter(Agency::isActive).toList();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (Agency a : agencias) {
            try {
                AgencyContext.callAs(a.getId(), () -> {
                    tx.executeWithoutResult(s -> task.accept(a.getId()));
                    return null;
                });
            } catch (RuntimeException e) {
                log.error("Job {} fallo en la agencia {} ({}): {}", job, a.getName(), a.getId(), e.getMessage(), e);
            }
        }
    }
}
