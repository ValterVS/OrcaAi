package com.orcaai.team;

import static com.orcaai.team.TeamFixture.PASSWORD;
import static com.orcaai.team.TeamFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
        "orcaai.team.invitations-per-user-per-hour=2",
        "orcaai.team.invitations-per-organization-per-hour=3"
})
class InvitationRateLimitIntegrationTest {

    @Autowired
    TeamFixture team;

    @Autowired
    TestData testData;

    @Test
    void limitsInvitationEmailsPerUserAndPerOrganizationWithoutBlockingAcceptance() throws Exception {
        Organization organization = testData.organization();
        User owner = testData.user(organization, "senha qualquer longa", Role.OWNER);
        User admin = testData.user(organization, "senha qualquer longa", Role.ADMIN);
        String accepted = uniqueEmail();

        assertThat(team.invite(owner, accepted, "MEMBER").getStatus()).isEqualTo(201);
        assertThat(team.invite(owner, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(201);
        assertThat(team.invite(owner, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(429);

        assertThat(team.invite(admin, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(201);
        assertThat(team.invite(admin, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(429);

        assertThat(team.accept(team.awaitToken(accepted, 1), "Pessoa", PASSWORD).getStatus()).isEqualTo(201);
    }
}
