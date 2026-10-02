package com.orcaai.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("orcaai.security.rate-limit")
public record AuthRateLimitProperties(
        @Valid @NotNull Login login,
        @Valid @NotNull PerAddress signup,
        @Valid @NotNull PerAddress emailRequests,
        @Valid @NotNull PerAddress tokenSubmissions) {

    public record Login(
            @Positive int maxAttemptsPerAddress,
            @Positive int maxFailuresPerAddressAndAccount,
            @NotNull Duration window) {
    }

    public record PerAddress(@Positive int maxAttemptsPerAddress, @NotNull Duration window) {
    }
}
