package com.orcaai.identity;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class UserSessionsIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    UserSessions userSessions;

    @Test
    void revokeAllEndsEverySessionOfThatUserOnly() throws Exception {
        Organization organization = testData.organization();
        User target = testData.user(organization, PASSWORD, Role.ADMIN);
        User colleague = testData.user(organization, PASSWORD, Role.MEMBER);
        Cookie laptop = login(target);
        Cookie phone = login(target);
        Cookie colleagueSession = login(colleague);

        userSessions.revokeAll(target.getEmail().toUpperCase());

        mvc.perform(get("/api/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(colleagueSession)).andExpect(status().isOk());
    }

    private Cookie login(User user) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .param("email", user.getEmail())
                        .param("password", PASSWORD)
                        .with(csrfToken(mvc)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");
    }
}
