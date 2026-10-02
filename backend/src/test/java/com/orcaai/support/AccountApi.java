package com.orcaai.support;

import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public account endpoints, called over HTTP with a real CSRF token, as the frontend does.
 */
@Component
public class AccountApi {

    public static final String PASSWORD = "uma senha longa o bastante";

    private final MockMvc mvc;
    private final RecordingMailSender mail;

    AccountApi(MockMvc mvc, RecordingMailSender mail) {
        this.mvc = mvc;
        this.mail = mail;
    }

    public MockHttpServletResponse signup(String company, String owner, String email, String password) throws Exception {
        return postJson("/api/auth/signup", """
                {"companyName":"%s","ownerName":"%s","email":"%s","password":"%s"}"""
                .formatted(company, owner, email, password));
    }

    public MockHttpServletResponse verify(String token) throws Exception {
        return postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(token));
    }

    public MockHttpServletResponse resendVerification(String email) throws Exception {
        return postJson("/api/auth/resend-verification", "{\"email\":\"%s\"}".formatted(email));
    }

    public MockHttpServletResponse forgotPassword(String email) throws Exception {
        return postJson("/api/auth/forgot-password", "{\"email\":\"%s\"}".formatted(email));
    }

    public MockHttpServletResponse resetPassword(String token, String password) throws Exception {
        return postJson("/api/auth/reset-password",
                "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password));
    }

    public MockHttpServletResponse login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .param("email", email)
                        .param("password", password)
                        .with(csrfToken(mvc)))
                .andReturn().getResponse();
    }

    public MockHttpServletResponse me(Cookie session) throws Exception {
        return mvc.perform(get("/api/auth/me").cookie(session)).andReturn().getResponse();
    }

    /** Signs up and confirms the email, as a new customer would. */
    public void verifiedOwner(String company, String email, String password) throws Exception {
        signup(company, "Pessoa Dona", email, password);
        verify(mail.awaitToken(email, 1));
    }

    public MockHttpServletResponse postJson(String path, String body) throws Exception {
        return mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrfToken(mvc)))
                .andReturn().getResponse();
    }

    public static String uniqueEmail() {
        return "conta-" + java.util.UUID.randomUUID() + "@example.com";
    }
}
