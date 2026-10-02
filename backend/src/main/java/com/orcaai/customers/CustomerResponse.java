package com.orcaai.customers;

import java.time.Instant;
import java.util.UUID;

/** The version travels in the ETag header, not in the body. */
record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    static CustomerResponse of(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getNotes(), customer.isArchived(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
