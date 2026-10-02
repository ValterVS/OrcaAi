package com.orcaai.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Obtains a CSRF token the same way the frontend does (cookie + header).
 *
 * <p>Spring Security's {@code csrf()} test helper is avoided on purpose: it replaces the real token
 * repository in the shared filter chain with a session-based one, which hides misconfiguration and
 * leaks into other tests that reuse the same context.
 */
public final class CsrfSupport {

    private CsrfSupport() {
    }

    public static RequestPostProcessor csrfToken(MockMvc mvc) throws Exception {
        Cookie token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (token == null) {
            throw new IllegalStateException("XSRF-TOKEN cookie was not issued");
        }
        return request -> {
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) {
                cookies.addAll(Arrays.asList(request.getCookies()));
            }
            cookies.add(token);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }
}
