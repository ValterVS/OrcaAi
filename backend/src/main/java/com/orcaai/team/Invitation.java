package com.orcaai.team;

import com.orcaai.shared.persistence.TenantOwnedEntity;
import com.orcaai.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An invitation to join the current organization. Administrative access goes through Hibernate's
 * tenant filter; acceptance (public, by token) is a conditional SQL update in InvitationService.
 * Only the SHA-256 of the token is stored.
 */
@Entity
@Table(name = "organization_invitations")
class Invitation extends TenantOwnedEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private byte[] tokenHash;

    @Column(nullable = false)
    private UUID invitedByUserId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastSentAt;

    private Instant acceptedAt;

    private Instant revokedAt;

    protected Invitation() {
    }

    Invitation(String email, Role role, UUID invitedByUserId) {
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("Invitations never grant OWNER");
        }
        this.email = email;
        this.role = role;
        this.invitedByUserId = invitedByUserId;
    }

    /** New token and a fresh expiry; any previous link stops working. */
    void renew(byte[] tokenHash, UUID invitedByUserId, Instant now, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.invitedByUserId = invitedByUserId;
        this.lastSentAt = now;
        this.expiresAt = expiresAt;
    }

    void changeRole(Role role) {
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("Invitations never grant OWNER");
        }
        this.role = role;
    }

    void revoke(Instant now) {
        revokedAt = now;
    }

    boolean isPending() {
        return acceptedAt == null && revokedAt == null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    boolean wasSentAfter(Instant moment) {
        return lastSentAt.isAfter(moment);
    }

    String getEmail() {
        return email;
    }

    Role getRole() {
        return role;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getLastSentAt() {
        return lastSentAt;
    }
}
