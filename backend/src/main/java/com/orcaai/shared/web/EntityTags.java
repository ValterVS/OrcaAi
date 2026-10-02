package com.orcaai.shared.web;

import com.orcaai.shared.error.BadRequestException;
import com.orcaai.shared.error.PreconditionFailedException;
import com.orcaai.shared.error.PreconditionRequiredException;

/**
 * Optimistic locking over HTTP. Responses for a single resource carry {@code ETag: "<version>"};
 * changes to it must send that value back in {@code If-Match}. Missing → 428, stale → 412.
 */
public final class EntityTags {

    private EntityTags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    public static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Versão inválida.");
        }
    }

    public static void requireCurrent(long expectedVersion, long currentVersion) {
        if (expectedVersion != currentVersion) {
            throw new PreconditionFailedException();
        }
    }
}
