package com.orcaai.identity;

import com.orcaai.shared.security.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the public account endpoints, other than sign-up. */
final class AccountRequests {

    private AccountRequests() {
    }

    record EmailRequest(
            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "Informe um e-mail válido.")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
            String email) {

        EmailRequest {
            email = email == null ? null : email.trim();
        }
    }

    record TokenRequest(@NotBlank @Size(max = 100) String token) {

        @Override
        public String toString() {
            return "TokenRequest[token=<redacted>]";
        }
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 100) String token, @Password String password) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[token=<redacted>, password=<redacted>]";
        }
    }

    record Accepted(String message) {
    }
}
