package com.orcaai.support;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs code in its own transaction as an authenticated member of the given organization.
 */
@Component
public class TenantScope {

    private final TransactionTemplate transaction;

    TenantScope(TransactionTemplate transaction) {
        this.transaction = transaction;
    }

    public <T> T as(Organization organization, Supplier<T> action) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), organization.getId(), "member@example.com", null, Role.MEMBER, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        try {
            return transaction.execute(status -> action.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public void run(Organization organization, Runnable action) {
        as(organization, () -> {
            action.run();
            return null;
        });
    }

    public <T> T anonymous(Supplier<T> action) {
        SecurityContextHolder.clearContext();
        return transaction.execute(status -> action.get());
    }
}
