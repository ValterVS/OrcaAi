package com.orcaai.team;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("orcaai.team")
public record TeamProperties(@Positive int invitationsPerUserPerHour, @Positive int invitationsPerOrganizationPerHour) {
}
