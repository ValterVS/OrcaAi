package com.orcaai.identity;

import com.orcaai.users.User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

/**
 * Sessions keep a snapshot of the user's role and status. Any change to role, status, password or
 * email must call {@link #revokeAll} in the same operation; waiting for expiry is not acceptable.
 * Call it before the change commits: if the change then fails, the user only has to log in again.
 */
@Service
public class UserSessions {

    private final FindByIndexNameSessionRepository<?> sessions;

    UserSessions(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    /** Sessions are indexed by principal name; pass the email the sessions were created with. */
    public void revokeAll(String email) {
        sessions.findByPrincipalName(User.normalizeEmail(email)).keySet().forEach(sessions::deleteById);
    }
}
