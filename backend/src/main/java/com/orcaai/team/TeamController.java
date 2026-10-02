package com.orcaai.team;

import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.web.EntityTags;
import com.orcaai.team.TeamRequests.ChangeRoleRequest;
import com.orcaai.team.TeamRequests.InviteRequest;
import com.orcaai.team.TeamResponses.InvitationResponse;
import com.orcaai.team.TeamResponses.MemberResponse;
import com.orcaai.users.User;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The team of the authenticated user's organization. The organization always comes from the
 * session; no request carries it. Authorization lives in the services. Member changes require
 * If-Match with the version the client read; there is no DELETE.
 */
@RestController
@RequestMapping("/api/team")
class TeamController {

    private final MemberService members;
    private final InvitationService invitations;

    TeamController(MemberService members, InvitationService invitations) {
        this.members = members;
        this.invitations = invitations;
    }

    @GetMapping("/members")
    List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser actor) {
        return members.list(actor).stream().map(MemberResponse::of).toList();
    }

    @PutMapping("/members/{id}/role")
    ResponseEntity<MemberResponse> changeRole(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody ChangeRoleRequest request) {
        return withTag(members.changeRole(actor, id, EntityTags.expectedVersion(ifMatch), request.role().toRole()));
    }

    @PostMapping("/members/{id}/deactivate")
    ResponseEntity<MemberResponse> deactivate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return withTag(members.deactivate(actor, id, EntityTags.expectedVersion(ifMatch)));
    }

    @PostMapping("/members/{id}/reactivate")
    ResponseEntity<MemberResponse> reactivate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return withTag(members.reactivate(actor, id, EntityTags.expectedVersion(ifMatch)));
    }

    @GetMapping("/invitations")
    List<InvitationResponse> invitations() {
        Instant now = Instant.now();
        return invitations.pending().stream().map(invitation -> InvitationResponse.of(invitation, now)).toList();
    }

    @PostMapping("/invitations")
    ResponseEntity<InvitationResponse> invite(
            @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody InviteRequest request) {
        Invitation invitation = invitations.invite(actor, request.email(), request.role().toRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.of(invitation, Instant.now()));
    }

    @PostMapping("/invitations/{id}/resend")
    InvitationResponse resend(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return InvitationResponse.of(invitations.resend(actor, id), Instant.now());
    }

    @PostMapping("/invitations/{id}/revoke")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        invitations.revoke(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<MemberResponse> withTag(User user) {
        return ResponseEntity.ok().eTag(EntityTags.of(user.getVersion())).body(MemberResponse.of(user));
    }
}
