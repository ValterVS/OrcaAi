package com.orcaai.team;

import com.orcaai.identity.AccountEmailRequested;
import com.orcaai.identity.AccountProperties;
import com.orcaai.organizations.OrganizationRepository;
import com.orcaai.shared.error.BusinessException;
import com.orcaai.shared.error.ResourceNotFoundException;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import com.orcaai.shared.security.SecureTokens;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations to join the current organization.
 *
 * <p>At most one pending invitation per address: inviting the same address again renews it (new
 * token, new expiry, possibly new role), and the previous link stops working. Emails are sent only
 * after commit.
 */
@Service
class InvitationService {

    static final String INVITE_REJECTED = "Não foi possível enviar o convite para este endereço.";
    static final String RECENTLY_SENT = "Um convite foi enviado há pouco para este endereço. Aguarde alguns minutos para reenviar.";
    static final String NOT_PENDING = "Este convite não está mais pendente.";
    static final String ACCEPT_REJECTED = "Não foi possível aceitar este convite.";

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final AccountProperties properties;
    private final InvitationRateLimiter rateLimiter;

    InvitationService(
            InvitationRepository invitations,
            UserRepository users,
            OrganizationRepository organizations,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc,
            ApplicationEventPublisher events,
            AccountProperties properties,
            InvitationRateLimiter rateLimiter) {
        this.invitations = invitations;
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.events = events;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional(readOnly = true)
    public List<Invitation> pending() {
        return invitations.findByAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public Invitation invite(AuthenticatedUser actor, String email, Role role) {
        requireCanManage(actor, role);
        if (users.existsByEmail(email)) {
            // Same message whether the address is in this organization or another one.
            log.info("Invitation by user {} refused: address already has an account", actor.userId());
            throw new BusinessException(INVITE_REJECTED);
        }
        Invitation invitation = invitations.findByEmailAndAcceptedAtIsNullAndRevokedAtIsNull(email)
                .map(pending -> {
                    requireCanManage(actor, pending.getRole());
                    pending.changeRole(role);
                    return pending;
                })
                .orElseGet(() -> new Invitation(email, role, actor.userId()));
        return send(actor, invitation, "created");
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public Invitation resend(AuthenticatedUser actor, UUID id) {
        Invitation invitation = findPending(id);
        requireCanManage(actor, invitation.getRole());
        return send(actor, invitation, "resent");
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public void revoke(AuthenticatedUser actor, UUID id) {
        Invitation invitation = findPending(id);
        requireCanManage(actor, invitation.getRole());
        invitation.revoke(Instant.now());
        log.info("Invitation {} revoked by user {} in organization {}", id, actor.userId(), actor.organizationId());
    }

    /**
     * Public: the token is the only credential. Email, organization and role come from the
     * invitation. The conditional UPDATE lets exactly one of several concurrent requests consume it;
     * the unique email constraint backs up the account check.
     */
    @Transactional
    public void accept(String token, String name, String password) {
        if (!SecureTokens.isWellFormed(token)) {
            throw new BusinessException("Este convite é inválido.");
        }
        String passwordHash = passwordEncoder.encode(password);
        byte[] tokenHash = SecureTokens.hash(token);
        List<Map<String, Object>> consumed = jdbc.queryForList("""
                update organization_invitations
                   set accepted_at = now(), updated_at = now(), version = version + 1
                 where token_hash = ? and accepted_at is null and revoked_at is null and expires_at > now()
                returning id, organization_id, email, role""", tokenHash);
        if (consumed.isEmpty()) {
            throw new BusinessException(rejectionFor(tokenHash));
        }

        Map<String, Object> row = consumed.getFirst();
        String email = (String) row.get("email");
        Role role = Role.valueOf((String) row.get("role"));
        if (role == Role.OWNER || users.existsByEmail(email)) {
            throw new BusinessException(ACCEPT_REJECTED);
        }
        User user = new User((UUID) row.get("organization_id"), name, email, passwordHash, role);
        // The link arrived at this address, which proves it belongs to the person accepting.
        user.markEmailVerified(Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ACCEPT_REJECTED);
        }
        log.info("Invitation {} accepted: user {} joined organization {}", row.get("id"), user.getId(), user.getOrganizationId());
    }

    private Invitation send(AuthenticatedUser actor, Invitation invitation, String operation) {
        Instant now = Instant.now();
        if (invitation.getId() != null && invitation.wasSentAfter(now.minus(properties.emailCooldown()))) {
            throw new BusinessException(RECENTLY_SENT);
        }
        rateLimiter.acquire(actor.userId(), actor.organizationId());

        String token = SecureTokens.generate();
        invitation.renew(SecureTokens.hash(token), actor.userId(), now, now.plus(properties.invitationTtl()));
        Invitation saved = invitations.saveAndFlush(invitation);
        String organizationName = organizations.findById(actor.organizationId()).orElseThrow().getName();
        events.publishEvent(AccountEmailRequested.invitation(saved.getId(), saved.getEmail(), token, organizationName));
        log.info("Invitation {} {} by user {} in organization {}", saved.getId(), operation, actor.userId(), actor.organizationId());
        return saved;
    }

    private Invitation findPending(UUID id) {
        Invitation invitation = invitations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!invitation.isPending()) {
            throw new BusinessException(NOT_PENDING);
        }
        return invitation;
    }

    private static void requireCanManage(AuthenticatedUser actor, Role role) {
        if (!TeamPermissions.canManage(actor.role(), role)) {
            throw new AccessDeniedException("Not allowed to manage invitations for " + role);
        }
    }

    private String rejectionFor(byte[] tokenHash) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select accepted_at, revoked_at from organization_invitations where token_hash = ?", tokenHash);
        if (rows.isEmpty()) {
            return "Este convite é inválido.";
        }
        Map<String, Object> row = rows.getFirst();
        if (row.get("accepted_at") != null) {
            return "Este convite já foi utilizado.";
        }
        if (row.get("revoked_at") != null) {
            return "Este convite não está mais disponível.";
        }
        return "Este convite expirou. Peça um novo convite a quem convidou você.";
    }
}
