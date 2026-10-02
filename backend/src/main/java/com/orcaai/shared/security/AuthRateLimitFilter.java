package com.orcaai.shared.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orcaai.shared.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Fixed-window login limits, combining two keys:
 * <ul>
 *   <li>client address: every attempt counts (slows one source trying many accounts);</li>
 *   <li>submitted email: only failures count (slows distributed guessing on one account).</li>
 * </ul>
 * The email key does not depend on whether the account exists, so a 429 reveals nothing.
 *
 * <p>State is in memory, per instance. The client address is {@code getRemoteAddr()}, which is only
 * the real client when forwarded headers are configured for a trusted proxy (see docs/security.md).
 */
class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LOGIN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, SecurityConfig.LOGIN_PATH);
    private static final int MAX_EMAIL_LENGTH = 254;

    private final LoginRateLimitProperties properties;
    private final HandlerExceptionResolver resolver;
    private final Cache<String, AtomicInteger> attemptsByAddress;
    private final Cache<String, AtomicInteger> failuresByAccount;

    LoginRateLimitFilter(LoginRateLimitProperties properties, HandlerExceptionResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
        this.attemptsByAddress = newCounterCache(properties);
        this.failuresByAccount = newCounterCache(properties);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String account = accountKey(request.getParameter("email"));
        int addressAttempts = counter(attemptsByAddress, request.getRemoteAddr()).incrementAndGet();
        int accountFailures = counter(failuresByAccount, account).get();

        if (addressAttempts > properties.maxAttemptsPerAddress()
                || accountFailures >= properties.maxFailuresPerAccount()) {
            resolver.resolveException(request, response, null, new RateLimitExceededException(properties.window()));
            return;
        }

        chain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            counter(failuresByAccount, account).incrementAndGet();
        }
    }

    private static String accountKey(String email) {
        if (email == null) {
            return "";
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_EMAIL_LENGTH ? normalized.substring(0, MAX_EMAIL_LENGTH) : normalized;
    }

    private static AtomicInteger counter(Cache<String, AtomicInteger> cache, String key) {
        return cache.get(key, k -> new AtomicInteger());
    }

    private static Cache<String, AtomicInteger> newCounterCache(LoginRateLimitProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.window())
                .maximumSize(100_000)
                .build();
    }
}
