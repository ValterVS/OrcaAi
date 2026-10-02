package com.orcaai.shared.error;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orcaai.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ErrorHandlingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void invalidBodyReturnsFieldViolations() throws Exception {
        mvc.perform(post("/api/test/validation")
                        .with(user("member")).with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/test/validation")
                        .with(user("member")).with(csrfToken(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedErrorDoesNotLeakInternals() throws Exception {
        mvc.perform(get("/api/test/failure").with(user("member")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno."))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));
    }

    @Test
    void notFoundIsReportedAsProblem() throws Exception {
        mvc.perform(get("/api/test/missing").with(user("member")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/test/owner-only").with(user("member").roles("MEMBER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/test/owner-only").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk());
    }
}
