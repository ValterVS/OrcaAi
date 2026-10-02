package com.orcaai.team;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orcaai.shared.error.RateLimitExceededException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Caps how many invitation emails (new or resent) an authenticated user and an organization can
 * trigger per hour. In memory, per instance, like the other limits. It never affects the invited
 * person: accepting an invitation is not counted here.
 */
@Component
class InvitationRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final TeamProperties limits;
    private final Cache<UUID, AtomicInteger> byUser = counters();
    private final Cache<UUID, AtomicInteger> byOrganization = counters();

    InvitationRateLimiter(TeamProperties limits) {
        this.limits = limits;
    }

    void acquire(UUID userId, UUID organizationId) {
        AtomicInteger user = byUser.get(userId, key -> new AtomicInteger());
        AtomicInteger organization = byOrganization.get(organizationId, key -> new AtomicInteger());
        if (user.get() >= limits.invitationsPerUserPerHour()
                || organization.get() >= limits.invitationsPerOrganizationPerHour()) {
            throw new RateLimitExceededException(WINDOW);
        }
        user.incrementAndGet();
        organization.incrementAndGet();
    }

    private static Cache<UUID, AtomicInteger> counters() {
        return Caffeine.newBuilder().expireAfterWrite(WINDOW).maximumSize(100_000).build();
    }
}
