package com.orcaai.identity;

import com.orcaai.shared.security.Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only what sign-up needs. Organization, role and ids are decided by the backend; unknown JSON
 * properties (such as organizationId or role) are rejected.
 */
record SignupRequest(
        @NotBlank(message = "Informe o nome da empresa.")
        @Size(max = 150, message = "O nome da empresa deve ter no máximo 150 caracteres.")
        String companyName,

        @NotBlank(message = "Informe seu nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String ownerName,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @Password
        String password) {

    SignupRequest {
        companyName = trim(companyName);
        ownerName = trim(ownerName);
        email = trim(email);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    // Keeps the password out of logs and exception messages that print the request.
    @Override
    public String toString() {
        return "SignupRequest[companyName=" + companyName + ", ownerName=" + ownerName + "]";
    }
}
