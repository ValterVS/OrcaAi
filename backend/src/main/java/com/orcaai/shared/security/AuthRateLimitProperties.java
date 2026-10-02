package com.orcaai.shared.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("orcaai.security.login-rate-limit")
public record LoginRateLimitProperties(
        @Positive int maxAttemptsPerAddress, @Positive int maxFailuresPerAccount, @NotNull Duration window) {
}
