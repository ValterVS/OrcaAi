package com.orcaai.shared.tenancy;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TenantScope;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression: Spring Session once ran on the JPA transaction manager. Loading a session then opened
 * a Hibernate session, which resolved the tenant from the security context, which loaded the
 * session again (StackOverflowError). See SessionConfig.
 */
@IntegrationTest
class SessionTenancyRegressionTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    TenantScope tenant;

    @Autowired
    TenancyProbeRepository probes;

    @Autowired
    JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    TransactionTemplate applicationTransactions;

    @Test
    void storedSessionDrivesTenantScopedJpaAccess() throws Exception {
        Organization orgA = testData.organization();
        Organization orgB = testData.organization();
        User userOfB = testData.user(orgB, PASSWORD, Role.MEMBER);
        tenant.as(orgA, () -> probes.save(new TenancyProbe("of-a")));
        tenant.as(orgB, () -> probes.save(new TenancyProbe("of-b")));

        Cookie session = mvc.perform(post("/api/auth/login")
                        .param("email", userOfB.getEmail())
                        .param("password", PASSWORD)
                        .with(csrfToken(mvc)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/api/test/tenancy-probes").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("of-b"));
    }

    @Test
    void springSessionUsesPlainJdbcTransactions() {
        TransactionTemplate sessionTransactions =
                (TransactionTemplate) ReflectionTestUtils.getField(sessionRepository, "transactionOperations");

        assertThat(sessionTransactions.getTransactionManager()).isExactlyInstanceOf(JdbcTransactionManager.class);
        assertThat(applicationTransactions.getTransactionManager()).isInstanceOf(TenantTransactionManager.class);
    }
}
