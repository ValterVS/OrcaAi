package com.orcaai.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container shared by every test context. Flyway runs as the schema owner and the
 * application as the restricted runtime user, as in production, so a migration that forgets to
 * grant a privilege fails the tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine").withInitScript("db/test-database-roles.sql");

    static {
        POSTGRES.start();
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties() {
        return registry -> {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "orcaai_app");
            registry.add("spring.datasource.password", () -> "app");
            registry.add("spring.flyway.user", () -> "orcaai_owner");
            registry.add("spring.flyway.password", () -> "owner");
        };
    }
}
