package com.orcaai.identity;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class AuthenticationIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    Organization organization;
    User user;

    @BeforeEach
    void setUp() {
        organization = testData.organization();
        user = testData.user(organization, PASSWORD, Role.ADMIN);
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void csrfEndpointIssuesReadableTokenCookieWithoutCreatingSession() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", user.getEmail()).param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithForgedCsrfHeaderIsRejected() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/auth/login")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", "forged")
                        .param("email", user.getEmail())
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordAndUnknownEmailFailTheSameWay() throws Exception {
        mvc.perform(login(user.getEmail(), "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Não autenticado."));
        mvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Não autenticado."));
    }

    @Test
    void loginCreatesSessionBoundToUserOrganization() throws Exception {
        MvcResult result = mvc.perform(login(user.getEmail().toUpperCase(), PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.organizationId").value(organization.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        Cookie session = mvc.perform(login(user.getEmail(), PASSWORD))
                .andReturn().getResponse().getCookie("SESSION");

        mvc.perform(post("/api/auth/logout").cookie(session).with(csrfToken(mvc)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        return post("/api/auth/login").param("email", email).param("password", password).with(csrfToken(mvc));
    }
}
