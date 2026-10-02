package com.orcaai.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orcaai.shared.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Fixed-window limits for the unauthenticated auth endpoints.
 *
 * <ul>
 *   <li>Login, per client address: every attempt counts.</li>
 *   <li>Login, per address + submitted email: only failures count. There is deliberately no limit
 *       on the email alone, which would let anyone lock a victim out by failing on purpose.</li>
 *   <li>Sign-up, per client address: every attempt counts.</li>
 * </ul>
 * Keys never depend on whether an account exists, so a 429 reveals nothing about accounts.
 *
 * <p>State is in memory, per instance. The client address is {@code getRemoteAddr()}, which is only
 * the real client when forwarded headers are configured for a trusted proxy (see docs/security.md).
 */
class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LOGIN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, SecurityConfig.LOGIN_PATH);
    private static final RequestMatcher SIGNUP =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, SecurityConfig.SIGNUP_PATH);
    private static final int MAX_EMAIL_LENGTH = 254;

    private final AuthRateLimitProperties properties;
    private final HandlerExceptionResolver resolver;
    private final Cache<String, AtomicInteger> loginAttemptsByAddress;
    private final Cache<String, AtomicInteger> loginFailuresByAddressAndAccount;
    private final Cache<String, AtomicInteger> signupAttemptsByAddress;

    AuthRateLimitFilter(AuthRateLimitProperties properties, HandlerExceptionResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
        this.loginAttemptsByAddress = counters(properties.login().window());
        this.loginFailuresByAddressAndAccount = counters(properties.login().window());
        this.signupAttemptsByAddress = counters(properties.signup().window());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request) && !SIGNUP.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SIGNUP.matches(request)) {
            filterSignup(request, response, chain);
        } else {
            filterLogin(request, response, chain);
        }
    }

    private void filterSignup(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthRateLimitProperties.Signup limits = properties.signup();
        if (increment(signupAttemptsByAddress, request.getRemoteAddr()) > limits.maxAttemptsPerAddress()) {
            reject(request, response, limits.window());
            return;
        }
        chain.doFilter(request, response);
    }

    private void filterLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthRateLimitProperties.Login limits = properties.login();
        String address = request.getRemoteAddr();
        String addressAndAccount = address + "|" + accountKey(request.getParameter("email"));

        int attempts = increment(loginAttemptsByAddress, address);
        int failures = counter(loginFailuresByAddressAndAccount, addressAndAccount).get();
        if (attempts > limits.maxAttemptsPerAddress() || failures >= limits.maxFailuresPerAddressAndAccount()) {
            reject(request, response, limits.window());
            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            increment(loginFailuresByAddressAndAccount, addressAndAccount);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Duration retryAfter) {
        resolver.resolveException(request, response, null, new RateLimitExceededException(retryAfter));
    }

    private static String accountKey(String email) {
        if (email == null) {
            return "";
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_EMAIL_LENGTH ? normalized.substring(0, MAX_EMAIL_LENGTH) : normalized;
    }

    private static int increment(Cache<String, AtomicInteger> cache, String key) {
        return counter(cache, key).incrementAndGet();
    }

    private static AtomicInteger counter(Cache<String, AtomicInteger> cache, String key) {
        return cache.get(key, k -> new AtomicInteger());
    }

    private static Cache<String, AtomicInteger> counters(Duration window) {
        return Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(100_000)
                .build();
    }
}
