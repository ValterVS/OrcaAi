package com.orcaai.identity;

import java.util.UUID;

/**
 * Published inside the transaction that caused it; delivered only after it commits.
 * {@code token} is null for notifications that carry no link.
 */
record AccountEmailRequested(Kind kind, UUID userId, String email, String token) {

    enum Kind {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        PASSWORD_CHANGED
    }

    // Events can end up in logs or debugger output; keep the token and the address out.
    @Override
    public String toString() {
        return "AccountEmailRequested[kind=" + kind + ", userId=" + userId + "]";
    }
}
