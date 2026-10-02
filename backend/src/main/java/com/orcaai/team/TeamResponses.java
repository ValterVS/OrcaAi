package com.orcaai.team;

import com.orcaai.shared.security.Role;
import com.orcaai.users.User;
import java.time.Instant;
import java.util.UUID;

final class TeamResponses {

    private TeamResponses() {
    }

    /**
     * {@code version} is listed so the team screen can send If-Match for an action on any row;
     * single-member responses also carry it as ETag.
     */
    record MemberResponse(UUID id, String name, String email, Role role, boolean active, long version) {

        static MemberResponse of(User user) {
            return new MemberResponse(
                    user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(), user.getVersion());
        }
    }

    record InvitationResponse(UUID id, String email, Role role, Instant expiresAt, boolean expired, Instant lastSentAt) {

        static InvitationResponse of(Invitation invitation, Instant now) {
            return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
                    invitation.getExpiresAt(), invitation.isExpired(now), invitation.getLastSentAt());
        }
    }
}
