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

class AuthRateLimitFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new AuthRateLimitProperties(
                    new AuthRateLimitProperties.Login(6, 2, Duration.ofMinutes(1)),
                    new AuthRateLimitProperties.Signup(2, Duration.ofMinutes(1))),
            resolver);

    @Test
    void limitsAllLoginAttemptsFromOneAddress() throws Exception {
        for (int i = 0; i < 6; i++) {
            assertThat(login("10.0.0.1", "user" + i + "@example.com", 204)).isTrue();
        }

        assertThat(login("10.0.0.1", "another@example.com", 204)).isFalse();
        assertThat(login("10.0.0.2", "another@example.com", 204)).isTrue();
    }

    @Test
    void limitsFailuresForSameAccountFromSameAddress() throws Exception {
        assertThat(login("10.0.0.1", "victim@example.com", 401)).isTrue();
        assertThat(login("10.0.0.1", " Victim@Example.com ", 401)).isTrue();

        assertThat(login("10.0.0.1", "victim@example.com", 204)).isFalse();
        assertThat(login("10.0.0.1", "someone-else@example.com", 204)).isTrue();
    }

    @Test
    void failuresFromOtherAddressesDoNotLockTheAccountOut() throws Exception {
        assertThat(login("10.0.0.66", "victim@example.com", 401)).isTrue();
        assertThat(login("10.0.0.66", "victim@example.com", 401)).isTrue();
        assertThat(login("10.0.0.66", "victim@example.com", 204)).isFalse();

        assertThat(login("10.0.0.7", "victim@example.com", 204)).isTrue();
    }

    @Test
    void successfulLoginsDoNotCountAsFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(login("10.0.0.1", "user@example.com", 204)).isTrue();
        }
    }

    @Test
    void limitsSignupsPerAddress() throws Exception {
        assertThat(signup("10.0.0.1")).isTrue();
        assertThat(signup("10.0.0.1")).isTrue();

        assertThat(signup("10.0.0.1")).isFalse();
        assertThat(signup("10.0.0.2")).isTrue();
    }

    @Test
    void ignoresOtherRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    private boolean login(String address, String email, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setParameter("email", email);
        return reachesHandler(request, address, status);
    }

    private boolean signup(String address) throws Exception {
        return reachesHandler(new MockHttpServletRequest("POST", "/api/auth/signup"), address, 201);
    }

    /** Returns whether the request got past the filter; the handler then answers with {@code status}. */
    private boolean reachesHandler(MockHttpServletRequest request, String address, int status) throws Exception {
        request.setRemoteAddr(address);
        boolean[] reached = {false};
        FilterChain chain = (req, res) -> {
            reached[0] = true;
            ((HttpServletResponse) res).setStatus(status);
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return reached[0];
    }
}
