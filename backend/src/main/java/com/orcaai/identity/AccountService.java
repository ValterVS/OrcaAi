package com.orcaai.identity;

import static com.orcaai.identity.OneTimeTokens.Purpose.EMAIL_VERIFICATION;

import com.orcaai.identity.AccountEmailRequested.Kind;
import com.orcaai.identity.OneTimeTokens.RejectedException;
import com.orcaai.organizations.Organization;
import com.orcaai.organizations.OrganizationRepository;
import com.orcaai.shared.error.BusinessException;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AccountService {

    static final String SIGNUP_ACCEPTED =
            "Se os dados puderem ser utilizados, enviaremos as instruções para continuar o cadastro.";
    static final String RESEND_ACCEPTED =
            "Se houver uma confirmação pendente para este e-mail, enviaremos uma nova mensagem.";

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokens tokens;
    private final ApplicationEventPublisher events;
    private final AccountProperties properties;
    private final TransactionTemplate transaction;

    AccountService(
            OrganizationRepository organizations,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            OneTimeTokens tokens,
            ApplicationEventPublisher events,
            AccountProperties properties,
            TransactionTemplate transaction) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.events = events;
        this.properties = properties;
        this.transaction = transaction;
    }

    /**
     * Same outcome for new and already registered addresses, so sign-up cannot be used to find
     * accounts. A new address gets an organization, a pending OWNER and a verification email; an
     * existing pending account only gets the email again (within the cooldown); nothing about an
     * existing account is ever changed.
     *
     * <p>Sign-up runs without a tenant: organizations and users are not tenant-filtered, and the new
     * organization is the row just created, never a value from the request.
     */
    public void signup(SignupRequest request) {
        // Hashing first keeps the response time independent of whether the address is taken.
        String passwordHash = passwordEncoder.encode(request.password());
        String email = User.normalizeEmail(request.email());
        try {
            transaction.executeWithoutResult(status -> signupInTransaction(request, email, passwordHash));
        } catch (DataIntegrityViolationException ex) {
            if (!users.existsByEmail(email)) {
                throw ex;
            }
            // A concurrent sign-up created the account first; nothing of ours was kept.
            log.info("Sign-up for an address registered concurrently");
        }
    }

    private void signupInTransaction(SignupRequest request, String email, String passwordHash) {
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            log.info("Sign-up for existing user {}", existing.get().getId());
            sendVerificationIfPending(existing.get());
            return;
        }
        Organization organization = organizations.save(new Organization(request.companyName()));
        User owner = users.saveAndFlush(
                new User(organization.getId(), request.ownerName(), email, passwordHash, Role.OWNER));
        issueVerification(owner);
        log.info("Organization {} created by sign-up, owner {} pending verification", organization.getId(), owner.getId());
    }

    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(User.normalizeEmail(email)).ifPresent(this::sendVerificationIfPending);
    }

    @Transactional
    public void verifyEmail(String token) {
        UUID userId;
        try {
            userId = tokens.consume(EMAIL_VERIFICATION, token);
        } catch (RejectedException ex) {
            throw new BusinessException(switch (ex.reason()) {
                case INVALID -> "Este link de confirmação é inválido.";
                case EXPIRED -> "Este link de confirmação expirou. Solicite um novo envio.";
                case USED -> "Este link de confirmação já foi utilizado.";
            });
        }
        User user = users.findForIdentityFlow(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException("Este link de confirmação é inválido."));
        user.markEmailVerified(Instant.now());
        tokens.expireUnused(EMAIL_VERIFICATION, userId);
        log.info("Email verified for user {}", userId);
    }

    @Transactional(readOnly = true)
    public CurrentAccountResponse currentAccount(AuthenticatedUser principal) {
        User user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .filter(found -> found.isActive() && found.isEmailVerified())
                .orElseThrow(() -> new InsufficientAuthenticationException("Account is no longer active"));
        Organization organization = organizations.findById(principal.organizationId())
                .orElseThrow(() -> new InsufficientAuthenticationException("Organization not found"));
        return new CurrentAccountResponse(
                user.getId(), user.getName(), user.getRole(), organization.getId(), organization.getName());
    }

    private void sendVerificationIfPending(User user) {
        if (!user.isActive() || user.isEmailVerified()) {
            return;
        }
        if (tokens.issuedWithin(EMAIL_VERIFICATION, user.getId(), properties.emailCooldown())) {
            log.info("Verification email for user {} skipped: cooldown", user.getId());
            return;
        }
        issueVerification(user);
    }

    private void issueVerification(User user) {
        String token = tokens.issue(EMAIL_VERIFICATION, user.getId(), properties.emailVerificationTtl());
        events.publishEvent(new AccountEmailRequested(Kind.EMAIL_VERIFICATION, user.getId(), user.getEmail(), token));
    }
}
