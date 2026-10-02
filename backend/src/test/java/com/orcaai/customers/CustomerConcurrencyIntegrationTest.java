package com.orcaai.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TenantScope;
import com.orcaai.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two people save the same customer at the same moment, both from version 0: exactly one wins,
 * the other gets a conflict instead of silently overwriting.
 */
@IntegrationTest
class CustomerConcurrencyIntegrationTest {

    @Autowired
    CustomerService service;

    @Autowired
    TestData testData;

    @Autowired
    TenantScope tenant;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void concurrentEditsFromTheSameVersionCannotBothWin() throws Exception {
        Organization organization = testData.organization();
        UUID id = tenant.as(organization, () -> service.create(new CustomerDetails("Original", null, null, null)).getId());
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String name : List.of("Edição A", "Edição B")) {
                results.add(executor.submit(() -> {
                    authenticateAs(organization);
                    try {
                        // Both transactions read version 0 before either writes.
                        transaction.executeWithoutResult(status -> {
                            Customer customer = service.get(id);
                            await(bothLoaded);
                            service.update(id, customer.getVersion(), new CustomerDetails(name, null, null, null));
                        });
                        return "saved";
                    } catch (OptimisticLockingFailureException ex) {
                        return "conflict";
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("saved", "conflict");
        } finally {
            executor.shutdownNow();
        }
        assertThat(tenant.as(organization, () -> service.get(id).getVersion())).isEqualTo(1);
    }

    private static void authenticateAs(Organization organization) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), organization.getId(), "member@example.com", null, Role.MEMBER, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
