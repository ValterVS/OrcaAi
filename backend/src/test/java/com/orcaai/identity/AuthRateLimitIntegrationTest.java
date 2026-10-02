package com.orcaai.identity;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestPropertySource(properties = {
        "orcaai.security.rate-limit.login.max-attempts-per-address=8",
        "orcaai.security.rate-limit.login.max-failures-per-address-and-account=3",
        "orcaai.security.rate-limit.signup.max-attempts-per-address=2",
        "orcaai.security.rate-limit.email-requests.max-attempts-per-address=2"
})
class AuthRateLimitIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    User victim;

    @BeforeEach
    void setUp() {
        victim = testData.user(testData.organization(), PASSWORD, Role.OWNER);
    }

    @Test
    void existingAndUnknownAccountsLookIdentical() throws Exception {
        List<String> existing = failedLogins("10.10.0.1", victim.getEmail(), 4);
        List<String> unknown = failedLogins("10.10.0.2", "nobody-" + UUID.randomUUID() + "@example.com", 4);

        assertThat(existing).isEqualTo(unknown);
        assertThat(existing).last().asString().startsWith("429");
    }

    @Test
    void attackerFailuresDoNotLockTheVictimOut() throws Exception {
        failedLogins("10.10.0.3", victim.getEmail(), 4);

        assertThat(login("10.10.0.4", victim.getEmail(), PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void attackerAddressIsLimitedAcrossAccounts() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            statuses.add(login("10.10.0.5", "user" + i + "@example.com", "wrong-password").getStatus());
        }

        assertThat(statuses.subList(0, 8)).containsOnly(401);
        assertThat(statuses.get(8)).isEqualTo(429);
    }

    @Test
    void signupIsLimitedPerAddress() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            statuses.add(mvc.perform(post("/api/auth/signup")
                            .with(request -> {
                                request.setRemoteAddr("10.10.0.6");
                                return request;
                            })
                            .with(csrfToken(mvc))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"Empresa","ownerName":"Pessoa","email":"s-%s@example.com",
                                     "password":"uma senha longa o bastante"}""".formatted(UUID.randomUUID())))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(statuses).containsExactly(202, 202, 429);
    }

    @Test
    void emailRequestsAreLimitedPerAddressWithoutLockingTheAccount() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            statuses.add(mvc.perform(post("/api/auth/forgot-password")
                            .with(request -> {
                                request.setRemoteAddr("10.10.0.7");
                                return request;
                            })
                            .with(csrfToken(mvc))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\"}".formatted(victim.getEmail())))
                    .andReturn().getResponse().getStatus());
        }

        assertThat(statuses).containsExactly(202, 202, 429);
        assertThat(login("10.10.0.8", victim.getEmail(), PASSWORD).getStatus()).isEqualTo(204);
    }

    /** Status and body of each attempt, so responses can be compared byte for byte. */
    private List<String> failedLogins(String address, String email, int attempts) throws Exception {
        List<String> responses = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            MockHttpServletResponse response = login(address, email, "wrong-password");
            responses.add(response.getStatus() + " " + response.getContentAsString());
        }
        return responses;
    }

    private MockHttpServletResponse login(String address, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(address);
                            return request;
                        })
                        .param("email", email)
                        .param("password", password)
                        .with(csrfToken(mvc)))
                .andReturn().getResponse();
    }
}
