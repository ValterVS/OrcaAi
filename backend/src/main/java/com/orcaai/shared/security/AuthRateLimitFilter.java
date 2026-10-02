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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Fixed-window limits for the unauthenticated account endpoints.
 *
 * <ul>
 *   <li>Login, per client address: every attempt counts.</li>
 *   <li>Login, per address + submitted email: only failures count. There is deliberately no limit
 *       on the email alone, which would let anyone lock a victim out by failing on purpose.</li>
 *   <li>Sign-up, email requests (resend verification, forgot password) and token submissions
 *       (verify email, reset password, accept invitation): per client address, every attempt counts.</li>
 * </ul>
 * Keys never depend on whether an account exists, so a 429 reveals nothing about accounts. How
 * often an address receives emails is limited separately, by a cooldown in the database.
 *
 * <p>State is in memory, per instance. The client address is {@code getRemoteAddr()}, which is only
 * the real client when forwarded headers are configured for a trusted proxy (see docs/security.md).
 */
class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LOGIN = post(SecurityConfig.LOGIN_PATH);
    private static final int MAX_EMAIL_LENGTH = 254;

    private record AddressLimit(RequestMatcher matcher, AuthRateLimitProperties.PerAddress limits,
            Cache<String, AtomicInteger> attempts) {
    }

    private final AuthRateLimitProperties properties;
    private final HandlerExceptionResolver resolver;
    private final Cache<String, AtomicInteger> loginAttemptsByAddress;
    private final Cache<String, AtomicInteger> loginFailuresByAddressAndAccount;
    private final List<AddressLimit> addressLimits;

    AuthRateLimitFilter(AuthRateLimitProperties properties, HandlerExceptionResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
        this.loginAttemptsByAddress = counters(properties.login().window());
        this.loginFailuresByAddressAndAccount = counters(properties.login().window());
        this.addressLimits = List.of(
                addressLimit(properties.signup(), SecurityConfig.SIGNUP_PATH),
                addressLimit(properties.emailRequests(),
                        SecurityConfig.RESEND_VERIFICATION_PATH, SecurityConfig.FORGOT_PASSWORD_PATH),
                addressLimit(properties.tokenSubmissions(),
                        SecurityConfig.VERIFY_EMAIL_PATH, SecurityConfig.RESET_PASSWORD_PATH,
                        SecurityConfig.ACCEPT_INVITATION_PATH));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request) && addressLimitFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AddressLimit addressLimit = addressLimitFor(request);
        if (addressLimit != null) {
            filterPerAddress(addressLimit, request, response, chain);
        } else {
            filterLogin(request, response, chain);
        }
    }

    private void filterPerAddress(AddressLimit limit, HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (increment(limit.attempts(), request.getRemoteAddr()) > limit.limits().maxAttemptsPerAddress()) {
            reject(request, response, limit.limits().window());
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

    private AddressLimit addressLimitFor(HttpServletRequest request) {
        for (AddressLimit limit : addressLimits) {
            if (limit.matcher().matches(request)) {
                return limit;
            }
        }
        return null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Duration retryAfter) {
        resolver.resolveException(request, response, null, new RateLimitExceededException(retryAfter));
    }

    private static AddressLimit addressLimit(AuthRateLimitProperties.PerAddress limits, String... paths) {
        List<RequestMatcher> matchers = List.of(paths).stream().map(AuthRateLimitFilter::post).toList();
        return new AddressLimit(new OrRequestMatcher(matchers), limits, counters(limits.window()));
    }

    private static RequestMatcher post(String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path);
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
