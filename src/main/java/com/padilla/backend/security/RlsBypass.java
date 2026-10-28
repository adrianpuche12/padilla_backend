package com.padilla.backend.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * PRO-021: ejecuta una operacion de plataforma sin filtro de agencia (app.rls_bypass = on).
 * Siempre en una transaccion NUEVA (REQUIRES_NEW): si se sumara a una transaccion ya abierta, el
 * SET LOCAL de esa transaccion (con su agencia) seguiria vigente y el bypass no tendria efecto.
 */
@Component
public class RlsBypass {

    private final TransactionTemplate tx;

    public RlsBypass(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T call(Supplier<T> action) {
        return AgencyContext.callBypassingRls(() -> tx.execute(s -> action.get()));
    }

    public void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
