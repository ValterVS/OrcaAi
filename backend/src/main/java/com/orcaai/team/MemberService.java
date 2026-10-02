package com.orcaai.team;

import com.orcaai.identity.UserSessions;
import com.orcaai.shared.error.ResourceNotFoundException;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import com.orcaai.shared.web.EntityTags;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team members of the current organization. {@code users} is not tenant-filtered (login needs it
 * before any tenant exists), so every lookup here includes the organization of the authenticated
 * actor explicitly; a user of another organization is simply not found.
 *
 * <p>Order of checks: not found (404), not allowed (403), stale version (412). Role changes and
 * deactivation end every session of the target, so old authorities do not survive.
 */
@Service
class MemberService {

    private static final Comparator<User> BY_ROLE_THEN_NAME =
            Comparator.comparing(User::getRole).thenComparing(User::getName, String.CASE_INSENSITIVE_ORDER);

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final UserRepository users;
    private final UserSessions sessions;

    MemberService(UserRepository users, UserSessions sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<User> list(AuthenticatedUser actor) {
        return users.findByOrganizationId(actor.organizationId()).stream().sorted(BY_ROLE_THEN_NAME).toList();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public User changeRole(AuthenticatedUser actor, UUID targetId, long expectedVersion, Role newRole) {
        User target = find(actor, targetId);
        requireNotSelf(actor, target);
        if (!TeamPermissions.canChangeRole(actor.role(), target.getRole(), newRole)) {
            throw new AccessDeniedException("Role change not allowed");
        }
        EntityTags.requireCurrent(expectedVersion, target.getVersion());
        if (target.getRole() != newRole) {
            target.changeRole(newRole);
            users.saveAndFlush(target);
            sessions.revokeAll(target.getEmail());
            log.info("User {} changed role of user {} to {} in organization {}",
                    actor.userId(), targetId, newRole, actor.organizationId());
        }
        return target;
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public User deactivate(AuthenticatedUser actor, UUID targetId, long expectedVersion) {
        User target = findManageable(actor, targetId, expectedVersion);
        if (target.isActive()) {
            target.deactivate();
            users.saveAndFlush(target);
            sessions.revokeAll(target.getEmail());
            log.info("User {} deactivated user {} in organization {}", actor.userId(), targetId, actor.organizationId());
        }
        return target;
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public User reactivate(AuthenticatedUser actor, UUID targetId, long expectedVersion) {
        User target = findManageable(actor, targetId, expectedVersion);
        if (!target.isActive()) {
            target.reactivate();
            users.saveAndFlush(target);
            log.info("User {} reactivated user {} in organization {}", actor.userId(), targetId, actor.organizationId());
        }
        return target;
    }

    private User findManageable(AuthenticatedUser actor, UUID targetId, long expectedVersion) {
        User target = find(actor, targetId);
        requireNotSelf(actor, target);
        if (!TeamPermissions.canManage(actor.role(), target.getRole())) {
            throw new AccessDeniedException("Not allowed to manage this user");
        }
        EntityTags.requireCurrent(expectedVersion, target.getVersion());
        return target;
    }

    private User find(AuthenticatedUser actor, UUID targetId) {
        return users.findByIdAndOrganizationId(targetId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
    }

    private static void requireNotSelf(AuthenticatedUser actor, User target) {
        if (target.getId().equals(actor.userId())) {
            throw new AccessDeniedException("Users cannot change their own role or status");
        }
    }
}
