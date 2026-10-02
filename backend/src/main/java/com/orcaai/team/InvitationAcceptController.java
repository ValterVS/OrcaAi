package com.orcaai.team;

import com.orcaai.team.TeamRequests.AcceptInvitationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public (CSRF-protected, rate limited). Creates the account; no session is opened. */
@RestController
class InvitationAcceptController {

    private final InvitationService invitations;

    InvitationAcceptController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/api/invitations/accept")
    ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        invitations.accept(request.token(), request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
