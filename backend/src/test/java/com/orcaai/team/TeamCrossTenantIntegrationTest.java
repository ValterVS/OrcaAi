package com.orcaai.team;

import static com.orcaai.team.TeamFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The owner of A knows the exact ids of B's users and invitations and still cannot see or touch
 * them: every answer is "not found", as if the ids did not exist.
 */
@IntegrationTest
class TeamCrossTenantIntegrationTest {

    @Autowired
    TeamFixture team;

    @Autowired
    TestData testData;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    User ownerA;
    User ownerB;
    User memberB;
    Organization orgB;
    String invitationOfB;

    @BeforeEach
    void setUp() throws Exception {
        ownerA = testData.user(testData.organization(), "senha qualquer longa", Role.OWNER);
        orgB = testData.organization();
        ownerB = testData.user(orgB, "senha qualquer longa", Role.OWNER);
        memberB = testData.user(orgB, "senha qualquer longa", Role.MEMBER);
        invitationOfB = team.invitationId(team.invite(ownerB, uniqueEmail(), "MEMBER"));
        jdbc.update("update organization_invitations set last_sent_at = now() - interval '1 hour' where id = ?::uuid",
                invitationOfB);
    }

    @Test
    void usersOfAnotherOrganizationAreNotFound() throws Exception {
        String tag = team.memberTag(ownerB, memberB.getId());

        assertThat(team.changeRole(ownerA, memberB.getId(), tag, "ADMIN").getStatus()).isEqualTo(404);
        assertThat(team.memberAction(ownerA, memberB.getId(), "deactivate", tag).getStatus()).isEqualTo(404);
        assertThat(team.memberAction(ownerA, memberB.getId(), "reactivate", tag).getStatus()).isEqualTo(404);
        assertThat(team.memberAction(ownerA, ownerB.getId(), "deactivate", "\"0\"").getStatus()).isEqualTo(404);
        assertThat(team.members(ownerA).getContentAsString())
                .doesNotContain(memberB.getId().toString()).doesNotContain(memberB.getEmail());

        User untouched = users.findByIdAndOrganizationId(memberB.getId(), orgB.getId()).orElseThrow();
        assertThat(untouched.getRole()).isEqualTo(Role.MEMBER);
        assertThat(untouched.isActive()).isTrue();
    }

    @Test
    void invitationsOfAnotherOrganizationAreNotFound() throws Exception {
        assertThat(team.invitationAction(ownerA, invitationOfB, "resend").getStatus()).isEqualTo(404);
        assertThat(team.invitationAction(ownerA, invitationOfB, "revoke").getStatus()).isEqualTo(404);
        assertThat(team.pendingInvitations(ownerA).getContentAsString()).doesNotContain(invitationOfB);

        assertThat(jdbc.queryForObject("select revoked_at is null from organization_invitations where id = ?::uuid",
                Boolean.class, invitationOfB)).isTrue();
        assertThat(team.pendingInvitations(ownerB).getContentAsString()).contains(invitationOfB);
    }
}
