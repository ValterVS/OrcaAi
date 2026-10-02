package com.orcaai.identity;

import static com.orcaai.support.AccountApi.PASSWORD;
import static com.orcaai.support.AccountApi.uniqueEmail;
import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.shared.security.Role;
import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SignupIntegrationTest {

    @Autowired
    AccountApi api;

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TestData testData;

    @Test
    void createsOrganizationAndPendingOwnerAndSendsVerification() throws Exception {
        String company = uniqueCompany();
        String email = uniqueEmail();

        MockHttpServletResponse response = api.signup(company, "Maria Souza", email, PASSWORD);

        assertThat(response.getStatus()).isEqualTo(202);
        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.getName()).isEqualTo("Maria Souza");
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.isEmailVerified()).isFalse();
        assertThat(owner.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, owner.getPasswordHash())).isTrue();
        assertThat(organizationName(owner.getOrganizationId())).isEqualTo(company);
        assertThat(RecordingMailSender.subject(mail.awaitMessageTo(email, 1))).isEqualTo("Confirme seu e-mail no Orça Aí");
    }

    @Test
    void newAndExistingAddressesGetTheSameAnswer() throws Exception {
        String email = uniqueEmail();
        MockHttpServletResponse first = api.signup(uniqueCompany(), "Primeira", email, PASSWORD);
        String secondCompany = uniqueCompany();

        MockHttpServletResponse second = api.signup(secondCompany, "Segunda", email.toUpperCase(), "outra senha bem longa");

        assertThat(second.getStatus()).isEqualTo(first.getStatus()).isEqualTo(202);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(organizationsNamed(secondCompany)).isZero();
    }

    @Test
    void existingAccountIsNeverChangedBySignup() throws Exception {
        String email = uniqueEmail();
        String company = uniqueCompany();
        api.verifiedOwner(company, email, PASSWORD);
        User before = users.findByEmail(email).orElseThrow();

        api.signup("Empresa Invasora", "Invasor", email, "senha do invasor longa");

        User after = users.findByEmail(email).orElseThrow();
        assertThat(after.getName()).isEqualTo(before.getName());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(organizationName(after.getOrganizationId())).isEqualTo(company);
        assertThat(api.login(email, PASSWORD).getStatus()).isEqualTo(204);
        assertThat(api.login(email, "senha do invasor longa").getStatus()).isEqualTo(401);
        assertThat(mail.settledMessagesTo(email)).hasSize(1);
    }

    @Test
    void storesEmailTrimmedAndLowercasedOnly() throws Exception {
        String local = "Maria.Silva+obra" + UUID.randomUUID();

        api.signup(uniqueCompany(), "Maria", "  " + local + "@Example.COM ", PASSWORD);

        assertThat(users.findByEmail(local.toLowerCase() + "@example.com")).isPresent();
    }

    @Test
    void ignoresRoleAndOrganizationSentByClient() throws Exception {
        var existingOrganization = testData.organization();
        String email = uniqueEmail();

        api.postJson("/api/auth/signup", """
                {"companyName":"%s","ownerName":"Joao","email":"%s","password":"%s",
                 "role":"MEMBER","organizationId":"%s","id":"%s","emailVerifiedAt":"2026-01-01T00:00:00Z"}"""
                .formatted(uniqueCompany(), email, PASSWORD, existingOrganization.getId(), UUID.randomUUID()));

        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        assertThat(owner.getOrganizationId()).isNotEqualTo(existingOrganization.getId());
        assertThat(owner.isEmailVerified()).isFalse();
    }

    @Test
    void rejectsPasswordsOutsideThePolicy() throws Exception {
        for (String password : new String[] {"curta", "x".repeat(65), "ç".repeat(40)}) {
            String email = uniqueEmail();
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).with(csrfToken(mvc))
                            .content("""
                                    {"companyName":"Empresa","ownerName":"Pessoa","email":"%s","password":"%s"}"""
                                    .formatted(email, password)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));
            assertThat(users.findByEmail(email)).isEmpty();
        }
    }

    @Test
    void rejectsInvalidFields() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).with(csrfToken(mvc))
                        .content("""
                                {"companyName":" ","ownerName":"","email":"not-an-email","password":"%s"}"""
                                .formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'companyName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'ownerName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void requiresCsrfToken() throws Exception {
        String company = uniqueCompany();

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","ownerName":"Pessoa","email":"%s","password":"%s"}"""
                                .formatted(company, uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(organizationsNamed(company)).isZero();
    }

    private String organizationName(UUID id) {
        return jdbc.queryForObject("select name from organizations where id = ?", String.class, id);
    }

    private int organizationsNamed(String name) {
        return jdbc.queryForObject("select count(*) from organizations where name = ?", Integer.class, name);
    }

    static String uniqueCompany() {
        return "Reformas " + UUID.randomUUID();
    }
}
