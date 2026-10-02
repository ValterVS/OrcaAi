package com.orcaai.customers;

import static com.orcaai.support.Authenticated.as;
import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TenantScope;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Organization B knows the exact id of A's customer and still finds nothing: through the API,
 * through JPA, and through plain SQL where only RLS stands in the way.
 */
@IntegrationTest
class CustomerIsolationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    TenantScope tenant;

    @Autowired
    CustomerRepository customers;

    @Autowired
    JdbcTemplate jdbc;

    Organization orgA;
    Organization orgB;
    User ownerA;
    User ownerB;
    UUID customerOfA;

    @BeforeEach
    void setUp() throws Exception {
        orgA = testData.organization();
        orgB = testData.organization();
        ownerA = testData.user(orgA, "senha qualquer longa", Role.OWNER);
        ownerB = testData.user(orgB, "senha qualquer longa", Role.OWNER);
        String body = mvc.perform(post("/api/customers").with(as(ownerA)).with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente Secreto da A\",\"email\":\"segredo@a.com\",\"phone\":\"5555\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        customerOfA = UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    void apiTreatsAnotherOrganizationsCustomerAsNonexistent() throws Exception {
        mvc.perform(get("/api/customers/" + customerOfA).with(as(ownerB))).andExpect(status().isNotFound());
        mvc.perform(put("/api/customers/" + customerOfA).with(as(ownerB)).with(csrfToken(mvc))
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sequestrado\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/customers/" + customerOfA + "/archive").with(as(ownerB)).with(csrfToken(mvc)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/customers/" + customerOfA + "/restore").with(as(ownerB)).with(csrfToken(mvc)))
                .andExpect(status().isNotFound());

        for (String status : List.of("ACTIVE", "ARCHIVED", "ALL")) {
            for (String term : new String[] {null, "Secreto", "segredo@a.com", "5555"}) {
                var request = get("/api/customers").param("status", status).with(as(ownerB));
                if (term != null) {
                    request.param("q", term);
                }
                String list = mvc.perform(request).andReturn().getResponse().getContentAsString();
                assertThat((Integer) JsonPath.read(list, "$.totalItems")).as(status + " " + term).isZero();
            }
        }

        mvc.perform(get("/api/customers/" + customerOfA).with(as(ownerA))).andExpect(status().isOk());
        assertThat(nameSeenByA()).isEqualTo("Cliente Secreto da A");
        assertThat(archivedSeenByA()).isFalse();
    }

    @Test
    void hibernateFilterHidesTheRowFromOrganizationB() {
        assertThat(tenant.as(orgB, () -> customers.findById(customerOfA))).isEmpty();
        assertThat(tenant.as(orgB, () -> customers.existsById(customerOfA))).isFalse();
        assertThat(tenant.as(orgB, () -> customers.findAll())).extracting(Customer::getId).doesNotContain(customerOfA);
        assertThat(tenant.as(orgA, () -> customers.findById(customerOfA))).isPresent();
    }

    @Test
    void rlsAloneKeepsOrganizationBOutOfAsRows() {
        // Plain SQL: Hibernate's filter is not involved, only the RLS policy.
        assertThat(tenant.as(orgA, () -> countById())).isOne();
        assertThat(tenant.as(orgB, () -> countById())).isZero();
        assertThat(tenant.as(orgB, () -> jdbc.queryForObject("select count(*) from customers", Integer.class)))
                .isZero();

        int updated = tenant.as(orgB, () ->
                jdbc.update("update customers set name = 'Sequestrado' where id = ?", customerOfA));
        int archived = tenant.as(orgB, () ->
                jdbc.update("update customers set archived_at = now() where id = ?", customerOfA));
        assertThat(updated).isZero();
        assertThat(archived).isZero();
        assertThat(nameSeenByA()).isEqualTo("Cliente Secreto da A");
        assertThat(archivedSeenByA()).isFalse();

        assertThatThrownBy(() -> tenant.run(orgB, () -> jdbc.update("""
                insert into customers (id, organization_id, name, created_at, updated_at)
                values (gen_random_uuid(), ?, 'Plantado em A', now(), now())""", orgA.getId())))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void rlsBlocksMovingARowIntoAnotherOrganization() {
        UUID customerOfB = UUID.randomUUID();
        tenant.run(orgB, () -> jdbc.update("""
                insert into customers (id, organization_id, name, created_at, updated_at)
                values (?, ?, 'Cliente da B', now(), now())""", customerOfB, orgB.getId()));

        assertThatThrownBy(() -> tenant.run(orgB, () -> jdbc.update(
                "update customers set organization_id = ? where id = ?", orgA.getId(), customerOfB)))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security");
        assertThat(tenant.as(orgA, () -> jdbc.queryForObject(
                "select count(*) from customers where id = ?", Integer.class, customerOfB))).isZero();
    }

    @Test
    void withoutAnOrganizationRlsShowsNothing() {
        assertThat(tenant.anonymous(() -> jdbc.queryForObject("select count(*) from customers", Integer.class)))
                .isZero();
    }

    @Test
    void runtimeUserCannotDeleteOrTruncateCustomers() {
        assertThatThrownBy(() -> tenant.run(orgA, () -> jdbc.update("delete from customers where id = ?", customerOfA)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> tenant.run(orgA, () -> jdbc.execute("truncate customers")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("alter table customers disable row level security"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("drop policy customers_tenant_isolation on customers"))
                .isInstanceOf(DataAccessException.class);
        assertThat(nameSeenByA()).isEqualTo("Cliente Secreto da A");
    }

    private Integer countById() {
        return jdbc.queryForObject("select count(*) from customers where id = ?", Integer.class, customerOfA);
    }

    private String nameSeenByA() {
        return tenant.as(orgA, () ->
                jdbc.queryForObject("select name from customers where id = ?", String.class, customerOfA));
    }

    private boolean archivedSeenByA() {
        return tenant.as(orgA, () -> jdbc.queryForObject(
                "select archived_at is not null from customers where id = ?", Boolean.class, customerOfA));
    }
}
