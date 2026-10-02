package com.orcaai.identity;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class SignupIntegrationTest {

    private static final String PASSWORD = "uma senha longa o bastante";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TestData testData;

    @Test
    void createsOrganizationAndOwnerTogether() throws Exception {
        String company = uniqueCompany();
        String email = uniqueEmail();

        signup(company, "Maria Souza", email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.getName()).isEqualTo("Maria Souza");
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, owner.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select name from organizations where id = ?", String.class,
                owner.getOrganizationId())).isEqualTo(company);
    }

    @Test
    void storesEmailTrimmedAndLowercasedOnly() throws Exception {
        String local = "Maria.Silva+obra" + UUID.randomUUID();

        signup(uniqueCompany(), "Maria", "  " + local + "@Example.COM ", PASSWORD).andExpect(status().isCreated());

        assertThat(users.findByEmail(local.toLowerCase() + "@example.com")).isPresent();
    }

    @Test
    void ignoresRoleAndOrganizationSentByClient() throws Exception {
        var existingOrganization = testData.organization();
        String email = uniqueEmail();
        String body = """
                {"companyName":"%s","ownerName":"Joao","email":"%s","password":"%s",
                 "role":"MEMBER","organizationId":"%s","id":"%s"}"""
                .formatted(uniqueCompany(), email, PASSWORD, existingOrganization.getId(), UUID.randomUUID());

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(csrfToken(mvc)))
                .andExpect(status().isCreated());

        User owner = users.findByEmail(email).orElseThrow();
        assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        assertThat(owner.getOrganizationId()).isNotEqualTo(existingOrganization.getId());
    }

    @Test
    void duplicateEmailIsRejectedGenericallyAndCreatesNothing() throws Exception {
        String email = uniqueEmail();
        signup(uniqueCompany(), "Primeira", email, PASSWORD).andExpect(status().isCreated());
        String secondCompany = uniqueCompany();

        signup(secondCompany, "Segunda", email.toUpperCase(), PASSWORD)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(AccountService.SIGNUP_REJECTED))
                .andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(organizationsNamed(secondCompany)).isZero();
    }

    @Test
    void rejectsPasswordsOutsideThePolicy() throws Exception {
        String accented = "ç".repeat(40);

        for (String password : new String[] {"curta", "x".repeat(65), accented}) {
            String email = uniqueEmail();
            signup(uniqueCompany(), "Pessoa", email, password)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));
            assertThat(users.findByEmail(email)).isEmpty();
        }
    }

    @Test
    void acceptsLongPassphrases() throws Exception {
        signup(uniqueCompany(), "Pessoa", uniqueEmail(), "frase longa com espacos e acentos é ok " + "x".repeat(20))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidFieldsWithoutCreatingAnything() throws Exception {
        signup(" ", "", "not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'companyName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'ownerName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void requiresCsrfToken() throws Exception {
        String company = uniqueCompany();

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body(company, "Pessoa", uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(organizationsNamed(company)).isZero();
    }

    @Test
    void signupThenLoginGivesSessionBoundToTheNewOrganization() throws Exception {
        String company = uniqueCompany();
        String email = uniqueEmail();
        signup(company, "Ana Lima", email, PASSWORD).andExpect(status().isCreated());
        UUID organizationId = users.findByEmail(email).orElseThrow().getOrganizationId();

        Cookie session = mvc.perform(post("/api/auth/login")
                        .param("email", email)
                        .param("password", PASSWORD)
                        .with(csrfToken(mvc)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Ana Lima"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()))
                .andExpect(jsonPath("$.organizationName").value(company));
        mvc.perform(get("/api/test/tenancy-probes").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void passwordBeyondBcryptLimitFailsLoginNormally() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .param("email", uniqueEmail())
                        .param("password", "x".repeat(200))
                        .with(csrfToken(mvc)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions signup(String company, String owner, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(company, owner, email, password))
                .with(csrfToken(mvc)));
    }

    private static String body(String company, String owner, String email, String password) {
        return """
                {"companyName":"%s","ownerName":"%s","email":"%s","password":"%s"}"""
                .formatted(company, owner, email, password);
    }

    private int organizationsNamed(String name) {
        return jdbc.queryForObject("select count(*) from organizations where name = ?", Integer.class, name);
    }

    private static String uniqueCompany() {
        return "Reformas " + UUID.randomUUID();
    }

    private static String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@example.com";
    }
}
