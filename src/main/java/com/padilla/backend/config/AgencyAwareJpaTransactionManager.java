package com.padilla.backend.config;

import com.padilla.backend.security.AgencyContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * PRO-021: unico punto donde se fija el tenant. Al iniciar cada transaccion ejecuta
 * set_config('app.current_agency', ?, true), equivalente a SET LOCAL: vale solo para esa
 * transaccion y no se filtra a la proxima conexion del pool. Las politicas RLS (V16) leen ese valor.
 */
public class AgencyAwareJpaTransactionManager extends JpaTransactionManager {

    public AgencyAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        UUID agency = AgencyContext.current().orElse(null);
        boolean bypass = AgencyContext.isBypass();
        if (agency == null && !bypass) {
            return;
        }
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        EntityManager em = holder.getEntityManager();
        em.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_agency', ?, true), set_config('app.rls_bypass', ?, true)")) {
                ps.setString(1, agency == null ? "" : agency.toString());
                ps.setString(2, bypass ? "on" : "off");
                ps.execute();
            }
        });
    }
}
