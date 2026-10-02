package com.orcaai.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * The editable fields of a customer, already normalized: text trimmed, blank optional fields
 * stored as null, email lowercased. The name keeps the casing the user typed.
 */
record CustomerDetails(
        @NotBlank(message = "Informe o nome do cliente.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String name,

        @Size(max = 40, message = "O telefone deve ter no máximo 40 caracteres.")
        String phone,

        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
        String email,

        @Size(max = 4000, message = "As observações devem ter no máximo 4000 caracteres.")
        String notes) {

    CustomerDetails {
        name = name == null ? null : name.trim();
        phone = blankToNull(phone);
        email = blankToNull(email);
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
        notes = blankToNull(notes);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
