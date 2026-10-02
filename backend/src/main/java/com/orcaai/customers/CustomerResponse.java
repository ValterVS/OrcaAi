package com.orcaai.customers;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code version} is sent back in the If-Match header when saving, so an edit based on stale data
 * is refused instead of silently overwriting someone else's change.
 */
record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String notes,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static CustomerResponse of(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getNotes(), customer.isArchived(), customer.getCreatedAt(), customer.getUpdatedAt(),
                customer.getVersion());
    }
}
