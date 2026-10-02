package com.orcaai.team;

import com.orcaai.shared.security.Role;

/**
 * Who may do what to whom in a team. OWNER is never a target: there is exactly one per
 * organization and no transfer of ownership yet.
 *
 * <ul>
 *   <li>OWNER: invites ADMIN or MEMBER, changes ADMIN ↔ MEMBER, deactivates and reactivates
 *       ADMIN and MEMBER, manages every invitation.</li>
 *   <li>ADMIN: invites MEMBER, deactivates and reactivates MEMBER, manages MEMBER invitations.</li>
 *   <li>MEMBER: sees the team, manages nothing.</li>
 * </ul>
 * Acting on oneself (role or status) is never allowed.
 */
final class TeamPermissions {

    private TeamPermissions() {
    }

    /**
     * Acting on a user or invitation with {@code target} role: deactivate, reactivate, invite,
     * resend, revoke.
     */
    static boolean canManage(Role actor, Role target) {
        return switch (actor) {
            case OWNER -> target == Role.ADMIN || target == Role.MEMBER;
            case ADMIN -> target == Role.MEMBER;
            case MEMBER -> false;
        };
    }

    static boolean canChangeRole(Role actor, Role from, Role to) {
        return actor == Role.OWNER && from != Role.OWNER && to != Role.OWNER;
    }
}
