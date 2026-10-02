package com.orcaai.shared.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal stored in the server-side session. It is the only trusted source of the current
 * organization.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private String passwordHash;

    public AuthenticatedUser(
            UUID userId, UUID organizationId, String email, String passwordHash, Role role, boolean enabled) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public UUID userId() {
        return userId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String email() {
        return email;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // Keeps the password hash out of the serialized session.
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
