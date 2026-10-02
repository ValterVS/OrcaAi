package com.orcaai.customers;

import com.orcaai.shared.error.BadRequestException;

/** Reads the version from an If-Match header: {@code "3"} or {@code 3}. */
final class IfMatch {

    private IfMatch() {
    }

    static long version(String header) {
        String value = header.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Versão inválida.");
        }
    }
}
