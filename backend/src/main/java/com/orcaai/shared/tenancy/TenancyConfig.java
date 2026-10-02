package com.orcaai.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration(proxyBeanMethods = false)
class TenancyConfig {

    // Replaces Boot's default JpaTransactionManager; Spring Session keeps its own JDBC transactions (SessionConfig).
    @Bean
    JpaTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory, ObjectProvider<TransactionManagerCustomizers> customizers) {
        JpaTransactionManager transactionManager = new TenantTransactionManager(entityManagerFactory);
        customizers.ifAvailable(c -> c.customize(transactionManager));
        return transactionManager;
    }
}
