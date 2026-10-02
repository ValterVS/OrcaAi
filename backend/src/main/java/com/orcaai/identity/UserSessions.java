package com.orcaai.identity;

import com.orcaai.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sessions keep a snapshot of the user's role and status. Any change to role, status, password or
 * email must call {@link #revokeAll} in the same transaction; waiting for expiry is not acceptable.
 *
 * <p>Sessions are deleted inside the caller's transaction (so a failed change keeps them) and again
 * right after commit: a login that used the old credentials while the change was still uncommitted
 * would otherwise keep its session.
 */
@Service
public class UserSessions {

    private static final Logger log = LoggerFactory.getLogger(UserSessions.class);

    // Spring Session indexes sessions by principal name, which is the normalized email.
    private static final String DELETE_SESSIONS = "delete from spring_session where principal_name = ?";

    private final JdbcTemplate jdbc;

    UserSessions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pass the email the sessions were created with. */
    public void revokeAll(String email) {
        String principal = User.normalizeEmail(email);
        jdbc.update(DELETE_SESSIONS, principal);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        jdbc.update(DELETE_SESSIONS, principal);
                    } catch (RuntimeException ex) {
                        log.error("Could not repeat session revocation after commit ({})", ex.getClass().getSimpleName());
                    }
                }
            });
        }
    }
}
