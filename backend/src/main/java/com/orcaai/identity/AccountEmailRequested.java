package com.orcaai.identity;

import java.util.UUID;

/**
 * Published inside the transaction that caused it; delivered only after it commits.
 * {@code subjectId} is the user (or, for invitations, the invitation) the email is about.
 * {@code token} is null for notifications without a link; {@code organizationName} is only used by
 * invitations.
 */
public record AccountEmailRequested(Kind kind, UUID subjectId, String email, String token, String organizationName) {

    public enum Kind {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        PASSWORD_CHANGED,
        INVITATION
    }

    public static AccountEmailRequested invitation(UUID invitationId, String email, String token, String organizationName) {
        return new AccountEmailRequested(Kind.INVITATION, invitationId, email, token, organizationName);
    }

    static AccountEmailRequested forUser(Kind kind, UUID userId, String email, String token) {
        return new AccountEmailRequested(kind, userId, email, token, null);
    }

    // Events can end up in logs or debugger output; keep the token and the address out.
    @Override
    public String toString() {
        return "AccountEmailRequested[kind=" + kind + ", subjectId=" + subjectId + "]";
    }
}
