package com.orcaai.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Single-use tokens sent by email. The raw token is returned once to be put in the email and is
 * never stored: the database keeps its SHA-256, which is enough for a 256-bit random value.
 *
 * <p>Must be called inside a transaction. Consumption is a conditional UPDATE, so two concurrent
 * requests with the same token cannot both succeed.
 */
@Component
class OneTimeTokens {

    enum Purpose {
        EMAIL_VERIFICATION("email_verification_tokens"),
        PASSWORD_RESET("password_reset_tokens");

        private final String table;

        Purpose(String table) {
            this.table = table;
        }
    }

    enum Rejection {
        INVALID,
        EXPIRED,
        USED
    }

    static final class RejectedException extends RuntimeException {

        private final Rejection reason;

        RejectedException(Rejection reason) {
            super("Token rejected: " + reason);
            this.reason = reason;
        }

        Rejection reason() {
            return reason;
        }
    }

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    OneTimeTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a token and expires every unused token of the same purpose for this user. */
    String issue(Purpose purpose, UUID userId, Duration ttl) {
        // Serializes issuing per user, so concurrent requests cannot leave two valid tokens.
        jdbc.queryForObject("select id from users where id = ? for update", UUID.class, userId);
        expireUnused(purpose, userId);

        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        jdbc.update("insert into " + purpose.table + " (user_id, token_hash, created_at, expires_at)"
                        + " values (?, ?, now(), now() + ? * interval '1 second')",
                userId, hash(token), ttl.toSeconds());
        return token;
    }

    /** Marks the token as used and returns its user, or throws with the reason it cannot be used. */
    UUID consume(Purpose purpose, String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw new RejectedException(Rejection.INVALID);
        }
        byte[] tokenHash = hash(token);
        List<UUID> consumed = jdbc.queryForList(
                "update " + purpose.table + " set consumed_at = now()"
                        + " where token_hash = ? and consumed_at is null and expires_at > now() returning user_id",
                UUID.class, tokenHash);
        if (!consumed.isEmpty()) {
            return consumed.getFirst();
        }
        throw new RejectedException(diagnose(purpose, tokenHash));
    }

    void expireUnused(Purpose purpose, UUID userId) {
        jdbc.update("update " + purpose.table + " set expires_at = now()"
                + " where user_id = ? and consumed_at is null and expires_at > now()", userId);
    }

    boolean issuedWithin(Purpose purpose, UUID userId, Duration period) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from " + purpose.table
                        + " where user_id = ? and created_at > now() - ? * interval '1 second')",
                Boolean.class, userId, period.toSeconds()));
    }

    private Rejection diagnose(Purpose purpose, byte[] tokenHash) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select consumed_at from " + purpose.table + " where token_hash = ?", tokenHash);
        if (rows.isEmpty()) {
            return Rejection.INVALID;
        }
        return rows.getFirst().get("consumed_at") instanceof Timestamp ? Rejection.USED : Rejection.EXPIRED;
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
        }
    }
}
