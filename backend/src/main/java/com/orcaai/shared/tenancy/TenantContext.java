package com.orcaai.shared.tenancy;

import com.orcaai.shared.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current organization from the authenticated principal only.
 *
 * <p>The security context is thread-bound: async work and scheduled jobs do not inherit it and
 * must establish the organization explicitly.
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static Optional<UUID> currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.organizationId());
        }
        return Optional.empty();
    }

    public static UUID requireOrganizationId() {
        return currentOrganizationId().orElseThrow(() -> new AccessDeniedException("No organization in context"));
    }
}
