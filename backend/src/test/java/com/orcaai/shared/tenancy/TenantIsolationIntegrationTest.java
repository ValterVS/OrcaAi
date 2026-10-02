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
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tenant B knows the exact id of tenant A's row and still cannot read or change it.
 */
@IntegrationTest
class TenantIsolationIntegrationTest {

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
    void insertTakesOrganizationFromAuthenticatedUser() {
        TenancyProbe saved = tenant.as(orgB, () -> probes.save(new TenancyProbe("new")));

        assertThat(saved.getOrganizationId()).isEqualTo(orgB.getId());
    }

    @Test
    void findByIdDoesNotReturnOtherTenantsRow() {
        assertThat(tenant.as(orgB, () -> probes.findById(probeOfA))).isEmpty();
        assertThat(tenant.as(orgB, () -> probes.existsById(probeOfA))).isFalse();
        assertThat(tenant.as(orgA, () -> probes.findById(probeOfA))).isPresent();
    }

    @Test
    void listingOnlyContainsCurrentTenantsRows() {
        List<TenancyProbe> visibleToB = tenant.as(orgB, () -> probes.findAll());

        assertThat(visibleToB).extracting(TenancyProbe::getOrganizationId).containsOnly(orgB.getId());
        assertThat(visibleToB).extracting(TenancyProbe::getId).doesNotContain(probeOfA);
    }

    @Test
    void bulkUpdateCannotReachOtherTenantsRow() {
        int updated = tenant.as(orgB, () -> probes.renameInBulk(probeOfA, "hijacked"));

        assertThat(updated).isZero();
        assertThat(labelSeenByA()).isEqualTo("a");
    }

    @Test
    void mergingDetachedCopyOfOtherTenantsRowFails() {
        TenancyProbe detached = tenant.as(orgA, () -> probes.findById(probeOfA).orElseThrow());
        detached.rename("hijacked");

        assertThatThrownBy(() -> tenant.as(orgB, () -> probes.saveAndFlush(detached)))
                .isInstanceOf(RuntimeException.class);
        assertThat(labelSeenByA()).isEqualTo("a");
    }

    @Test
    void deleteCannotReachOtherTenantsRow() {
        TenancyProbe detached = tenant.as(orgA, () -> probes.findById(probeOfA).orElseThrow());

        tenant.run(orgB, () -> probes.deleteById(probeOfA));
        tenant.run(orgB, () -> probes.delete(detached));
        int bulkDeleted = tenant.as(orgB, () -> probes.deleteInBulk(probeOfA));

        assertThat(bulkDeleted).isZero();
        assertThat(tenant.as(orgA, () -> probes.existsById(probeOfA))).isTrue();
    }

    @Test
    void nativeQueryWithExplicitOrganizationFilterIsIsolated() {
        List<TenancyProbe> visibleToB =
                tenant.as(orgB, () -> probes.findAllNative(TenantContext.requireOrganizationId()));

        assertThat(visibleToB).extracting(TenancyProbe::getOrganizationId).containsOnly(orgB.getId());
    }

    @Test
    void withoutAuthenticatedOrganizationNothingIsVisibleOrWritable() {
        List<TenancyProbe> visible = tenant.anonymous(() -> probes.findAll());

        assertThat(visible).isEmpty();
        assertThatThrownBy(() -> tenant.anonymous(() -> probes.saveAndFlush(new TenancyProbe("orphan"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String labelSeenByA() {
        return tenant.as(orgA, () -> probes.findById(probeOfA).orElseThrow().getLabel());
    }
}
