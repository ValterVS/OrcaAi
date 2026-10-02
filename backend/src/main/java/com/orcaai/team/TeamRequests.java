package com.orcaai.team;

import com.orcaai.shared.security.Password;
import com.orcaai.shared.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Team request bodies. Unknown properties (organizationId, active, emailVerifiedAt, id...) are
 * rejected globally, and OWNER is not a value of {@link AssignableRole}, so it cannot be sent.
 */
final class TeamRequests {

    private TeamRequests() {
    }

    enum AssignableRole {
        ADMIN,
        MEMBER;

        Role toRole() {
            return Role.valueOf(name());
        }
    }

    record InviteRequest(
            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email,

            @NotNull(message = "Escolha o papel.")
            AssignableRole role) {

        InviteRequest {
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        }
    }

    record ChangeRoleRequest(@NotNull(message = "Escolha o papel.") AssignableRole role) {
    }

    /** Email, organization and role come from the invitation, never from this request. */
    record AcceptInvitationRequest(
            @NotBlank @Size(max = 100) String token,

            @NotBlank(message = "Informe seu nome.")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
            String name,

            @Password String password) {

        AcceptInvitationRequest {
            name = name == null ? null : name.trim();
        }

        @Override
        public String toString() {
            return "AcceptInvitationRequest[name=" + name + ", token=<redacted>, password=<redacted>]";
        }
    }
}
