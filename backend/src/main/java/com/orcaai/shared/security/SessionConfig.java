package com.orcaai.shared.security;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class SessionConfig {

    // Spring Session must not use the JPA transaction manager: opening a JPA session resolves the
    // tenant from the security context, which is itself loaded from the HTTP session (recursion).
    // Spring Session looks this bean up by name; defaultCandidate = false keeps it from replacing
    // the application's JPA TransactionTemplate.
    @Bean(defaultCandidate = false)
    TransactionOperations springSessionTransactionOperations(DataSource dataSource) {
        return new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }
}
