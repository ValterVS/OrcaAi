package com.orcaai.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The application runs as orcaai_app (member of orcaai_runtime); Flyway runs as orcaai_owner.
 */
@IntegrationTest
class DatabasePrivilegesIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void runtimeUserHasNoElevatedAttributesOrOwnership() {
        Map<String, Object> role = jdbc.queryForMap(
                "select rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb from pg_roles where rolname = current_user");

        assertThat(role).containsEntry("rolname", "orcaai_app")
                .containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolcreatedb", false);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_tables where schemaname = 'public' and tableowner = current_user", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select has_schema_privilege('public', 'CREATE')", Boolean.class)).isFalse();
    }

    @Test
    void runtimeUserCannotTouchFlywayHistory() {
        for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
            assertThat(jdbc.queryForObject(
                    "select has_table_privilege('flyway_schema_history', ?)", Boolean.class, privilege))
                    .as(privilege).isFalse();
        }
        assertThatThrownBy(() -> jdbc.update("update flyway_schema_history set success = false"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from flyway_schema_history"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void runtimeUserCannotChangeSchemaOrPolicies() {
        assertThatThrownBy(() -> jdbc.execute("create table attack (id int)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("alter table users add column attack int"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("create policy attack on users using (true)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("alter table tenancy_probes disable row level security"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void runtimeUserHasOnlyTheTablePrivilegesItNeeds() {
        assertThat(privilege("users", "DELETE")).isFalse();
        assertThat(privilege("organizations", "DELETE")).isFalse();
        assertThat(privilege("users", "TRUNCATE")).isFalse();
        assertThat(privilege("users", "UPDATE")).isTrue();
        assertThat(privilege("spring_session", "DELETE")).isTrue();
        assertThat(privilege("organization_invitations", "UPDATE")).isTrue();
        assertThat(privilege("organization_invitations", "DELETE")).isFalse();
        assertThat(privilege("organization_invitations", "TRUNCATE")).isFalse();
        assertThat(privilege("customers", "SELECT")).isTrue();
        assertThat(privilege("customers", "UPDATE")).isTrue();
        assertThat(privilege("customers", "DELETE")).isFalse();
        assertThat(privilege("customers", "TRUNCATE")).isFalse();
        assertThat(privilege("customers", "REFERENCES")).isFalse();
        assertThat(privilege("customers", "TRIGGER")).isFalse();
        for (String table : List.of("email_verification_tokens", "password_reset_tokens")) {
            assertThat(privilege(table, "SELECT")).as(table).isTrue();
            assertThat(privilege(table, "UPDATE")).as(table).isTrue();
            assertThat(privilege(table, "DELETE")).as(table).isFalse();
            assertThat(privilege(table, "TRUNCATE")).as(table).isFalse();
        }
    }

    @Test
    void migrationsRanAsSchemaOwner() throws Exception {
        try (Connection admin = DriverManager.getConnection(
                TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
                TestcontainersConfiguration.POSTGRES.getUsername(),
                TestcontainersConfiguration.POSTGRES.getPassword())) {
            assertThat(strings(admin, "select distinct installed_by from flyway_schema_history"))
                    .containsExactly("orcaai_owner");
            assertThat(strings(admin, "select distinct tableowner from pg_tables where schemaname = 'public'"))
                    .containsExactly("orcaai_owner");
        }
    }

    private boolean privilege(String table, String privilege) {
        return jdbc.queryForObject("select has_table_privilege(?, ?)", Boolean.class, table, privilege);
    }

    private static List<String> strings(Connection connection, String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (ResultSet rows = connection.createStatement().executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
