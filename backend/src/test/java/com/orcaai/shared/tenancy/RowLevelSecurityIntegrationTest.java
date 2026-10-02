package com.orcaai.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orcaai.organizations.Organization;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TenantScope;
import com.orcaai.support.TestData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the RLS policy on tenancy_probes with plain SQL, i.e. without Hibernate's filter.
 * Each block switches to a non-owner role with {@code SET LOCAL ROLE}; the organization comes from
 * {@link TenantTransactionManager}.
 */
@IntegrationTest
class RowLevelSecurityIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TenancyProbeRepository probes;

    @Autowired
    TestData testData;

    @Autowired
    TenantScope tenant;

    Organization orgA;
    Organization orgB;
    UUID probeOfA;

    @BeforeEach
    void setUp() {
        orgA = testData.organization();
        orgB = testData.organization();
        probeOfA = tenant.as(orgA, () -> probes.save(new TenancyProbe("a")).getId());
        tenant.as(orgB, () -> probes.save(new TenancyProbe("b")));
    }

    @Test
    void transactionExposesCurrentOrganizationToPostgres() {
        String setting = tenant.as(orgA, () ->
                jdbc.queryForObject("select current_setting('app.current_organization_id', true)", String.class));

        assertThat(setting).isEqualTo(orgA.getId().toString());
    }

    @Test
    void unfilteredSqlOnlySeesCurrentOrganizationRows() {
        List<UUID> visibleToB = tenant.as(orgB, () -> {
            useRestrictedRole();
            return jdbc.queryForList("select organization_id from tenancy_probes", UUID.class);
        });

        assertThat(visibleToB).isNotEmpty().containsOnly(orgB.getId());
    }

    @Test
    void unfilteredSqlCannotModifyOtherOrganizationRows() {
        int updated = tenant.as(orgB, () -> {
            useRestrictedRole();
            return jdbc.update("update tenancy_probes set label = 'hijacked' where id = ?", probeOfA);
        });
        int deleted = tenant.as(orgB, () -> {
            useRestrictedRole();
            return jdbc.update("delete from tenancy_probes where id = ?", probeOfA);
        });

        assertThat(updated).isZero();
        assertThat(deleted).isZero();
        assertThat(tenant.as(orgA, () -> probes.findById(probeOfA).orElseThrow().getLabel())).isEqualTo("a");
    }

    @Test
    void canInsertOnlyForCurrentOrganization() {
        tenant.run(orgB, () -> {
            useRestrictedRole();
            insertProbeFor(orgB);
        });

        assertThatThrownBy(() -> tenant.run(orgB, () -> {
            useRestrictedRole();
            insertProbeFor(orgA);
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("row-level security");
    }

    private void insertProbeFor(Organization organization) {
        jdbc.update("""
                insert into tenancy_probes (id, organization_id, label, created_at, updated_at)
                values (gen_random_uuid(), ?, 'direct', now(), now())""", organization.getId());
    }

    @Test
    void withoutOrganizationNothingIsVisible() {
        Integer count = tenant.anonymous(() -> {
            useRestrictedRole();
            return jdbc.queryForObject("select count(*) from tenancy_probes", Integer.class);
        });

        assertThat(count).isZero();
    }

    @Test
    void settingDoesNotSurviveOnPooledConnection() {
        Integer tenantConnection = tenant.as(orgA, () ->
                jdbc.queryForObject("select pg_backend_pid()", Integer.class));

        // Outside any transaction, so nothing sets the value again.
        var reused = jdbc.queryForMap(
                "select pg_backend_pid() as pid, current_setting('app.current_organization_id', true) as setting");

        assertThat(reused.get("pid")).isEqualTo(tenantConnection);
        assertThat((String) reused.get("setting")).isNullOrEmpty();
    }

    private void useRestrictedRole() {
        jdbc.execute("set local role rls_probe_app");
    }
}
