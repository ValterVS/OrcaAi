package com.orcaai.customers;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Case-insensitive "contains" on name, email and phone. The term is a bound parameter with LIKE
 * wildcards escaped, so user input never becomes part of the SQL.
 */
final class CustomerSearch {

    private static final char ESCAPE = '\\';

    private CustomerSearch() {
    }

    static Specification<Customer> matching(CustomerStatus status, String term) {
        return withStatus(status).and(containing(term));
    }

    private static Specification<Customer> withStatus(CustomerStatus status) {
        return (root, query, cb) -> switch (status) {
            case ACTIVE -> cb.isNull(root.get("archivedAt"));
            case ARCHIVED -> cb.isNotNull(root.get("archivedAt"));
            case ALL -> cb.conjunction();
        };
    }

    private static Specification<Customer> containing(String term) {
        if (term == null || term.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                cb.like(root.get("email"), pattern, ESCAPE),
                cb.like(cb.lower(root.get("phone")), pattern, ESCAPE));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
