package com.orcaai.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes the current organization to PostgreSQL as {@code app.current_organization_id} at the
 * start of every JPA transaction, for Row Level Security policies (defense in depth).
 *
 * <p>{@code set_config(..., true)} is transaction-scoped, so the value never survives on a pooled
 * connection. Without an authenticated organization the value is empty and policies match nothing.
 */
class TenantTransactionManager extends JpaTransactionManager {

    private static final String SET_ORGANIZATION = "select set_config('app.current_organization_id', ?, true)";

    TenantTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        try {
            String organizationId = TenantContext.currentOrganizationId().map(UUID::toString).orElse("");
            holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(SET_ORGANIZATION)) {
                    statement.setString(1, organizationId);
                    statement.execute();
                }
            });
        } catch (RuntimeException | Error ex) {
            // Never hand out a transaction whose tenant setting is unknown.
            EntityTransaction tx = holder.getEntityManager().getTransaction();
            if (tx.isActive()) {
                tx.rollback();
            }
            doCleanupAfterCompletion(transaction);
            throw ex;
        }
    }
}
