package com.orcaai.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Links in emails are built from {@code publicUrl}, never from the request Host header, so a forged
 * Host cannot redirect tokens to another site. The URL is validated at startup.
 */
@Validated
@ConfigurationProperties("orcaai.account")
public record AccountProperties(
        @NotNull URI publicUrl,
        @NotBlank @Email String mailFrom,
        @NotNull Duration emailVerificationTtl,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration invitationTtl,
        @NotNull Duration emailCooldown) {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    public AccountProperties {
        if (publicUrl != null) {
            String scheme = publicUrl.getScheme();
            String host = publicUrl.getHost();
            boolean local = host != null && LOCAL_HOSTS.contains(host);
            if (host == null || publicUrl.getQuery() != null || publicUrl.getFragment() != null
                    || publicUrl.getUserInfo() != null
                    || !("https".equals(scheme) || ("http".equals(scheme) && local))) {
                throw new IllegalArgumentException(
                        "orcaai.account.public-url must be an absolute https URL (http only for localhost)"
                                + " without query, fragment or credentials");
            }
        }
    }

    String link(String path, String token) {
        String base = publicUrl.toString().replaceAll("/+$", "");
        return base + path + "#token=" + token;
    }
}
