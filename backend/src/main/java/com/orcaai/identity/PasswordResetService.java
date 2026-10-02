package com.orcaai.identity;

import static com.orcaai.identity.OneTimeTokens.Purpose.EMAIL_VERIFICATION;
import static com.orcaai.identity.OneTimeTokens.Purpose.PASSWORD_RESET;

import com.orcaai.identity.AccountEmailRequested.Kind;
import com.orcaai.identity.OneTimeTokens.RejectedException;
import com.orcaai.shared.error.BusinessException;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts whose email is not verified yet may also reset their password: following the link
 * proves control of the mailbox, so a successful reset also marks the email as verified.
 * Inactive accounts never receive a token.
 */
@Service
class PasswordResetService {

    static final String REQUEST_ACCEPTED =
            "Se existir uma conta válida para este endereço, enviaremos instruções de recuperação.";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokens tokens;
    private final UserSessions sessions;
    private final ApplicationEventPublisher events;
    private final AccountProperties properties;

    PasswordResetService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            OneTimeTokens tokens,
            UserSessions sessions,
            ApplicationEventPublisher events,
            AccountProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.sessions = sessions;
        this.events = events;
        this.properties = properties;
    }

    @Transactional
    public void requestReset(String email) {
        User user = users.findByEmail(User.normalizeEmail(email)).filter(User::isActive).orElse(null);
        if (user == null) {
            return;
        }
        if (tokens.issuedWithin(PASSWORD_RESET, user.getId(), properties.emailCooldown())) {
            log.info("Password reset email for user {} skipped: cooldown", user.getId());
            return;
        }
        String token = tokens.issue(PASSWORD_RESET, user.getId(), properties.passwordResetTtl());
        events.publishEvent(AccountEmailRequested.forUser(Kind.PASSWORD_RESET, user.getId(), user.getEmail(), token));
        log.info("Password reset requested for user {}", user.getId());
    }

    /** Changes the password, consumes the token, expires the others and ends every session. No login follows. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        UUID userId;
        try {
            userId = tokens.consume(PASSWORD_RESET, token);
        } catch (RejectedException ex) {
            throw new BusinessException(switch (ex.reason()) {
                case INVALID -> "Este link de redefinição é inválido.";
                case EXPIRED -> "Este link de redefinição expirou. Solicite uma nova redefinição.";
                case USED -> "Este link de redefinição já foi utilizado.";
            });
        }
        User user = users.findForIdentityFlow(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException("Este link de redefinição é inválido."));

        user.changePassword(passwordEncoder.encode(newPassword));
        user.markEmailVerified(Instant.now());
        tokens.expireUnused(PASSWORD_RESET, userId);
        tokens.expireUnused(EMAIL_VERIFICATION, userId);
        sessions.revokeAll(user.getEmail());
        events.publishEvent(AccountEmailRequested.forUser(Kind.PASSWORD_CHANGED, userId, user.getEmail(), null));
        log.info("Password reset completed for user {}", userId);
    }
}
