package com.orcaai.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

class LoginRateLimitFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final LoginRateLimitFilter filter =
            new LoginRateLimitFilter(new LoginRateLimitProperties(5, 2, Duration.ofMinutes(1)), resolver);

    @Test
    void limitsAttemptsPerAddress() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt("10.0.0.1", "user" + i + "@example.com", 204)).isTrue();
        }

        assertThat(attempt("10.0.0.1", "other@example.com", 204)).isFalse();
        assertThat(attempt("10.0.0.2", "other@example.com", 204)).isTrue();
    }

    @Test
    void limitsFailuresPerAccountAcrossAddresses() throws Exception {
        assertThat(attempt("10.0.0.1", "victim@example.com", 401)).isTrue();
        assertThat(attempt("10.0.0.2", " Victim@Example.com ", 401)).isTrue();

        assertThat(attempt("10.0.0.3", "victim@example.com", 204)).isFalse();
        assertThat(attempt("10.0.0.3", "someone-else@example.com", 204)).isTrue();
    }

    @Test
    void successfulAttemptsDoNotCountAgainstAccount() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(attempt("10.0.0." + i, "user@example.com", 204)).isTrue();
        }
    }

    @Test
    void ignoresOtherRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    /** Returns whether the request reached the login handler, which answers with {@code status}. */
    private boolean attempt(String address, String email, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(address);
        request.setParameter("email", email);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};
        FilterChain chain = (req, res) -> {
            reached[0] = true;
            ((HttpServletResponse) res).setStatus(status);
        };
        filter.doFilter(request, response, chain);
        return reached[0];
    }
}
