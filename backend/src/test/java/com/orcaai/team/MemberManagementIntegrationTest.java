package com.orcaai.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

@IntegrationTest
class MemberManagementIntegrationTest {

    private static final String PASSWORD = "senha do funcionario longa";

    @Autowired
    TeamFixture team;

    @Autowired
    AccountApi accounts;

    @Autowired
    TestData testData;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    Organization organization;
    User owner;
    User admin;
    User member;

    @BeforeEach
    void setUp() {
        organization = testData.organization();
        owner = testData.user(organization, PASSWORD, Role.OWNER);
        admin = testData.user(organization, PASSWORD, Role.ADMIN);
        member = testData.user(organization, PASSWORD, Role.MEMBER);
    }

    @Test
    void everyMemberSeesTheTeamOfTheirOwnOrganizationOnly() throws Exception {
        User stranger = testData.user(testData.organization(), PASSWORD, Role.OWNER);

        String body = team.members(member).getContentAsString();

        List<String> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).containsExactlyInAnyOrder(owner.getId().toString(), admin.getId().toString(), member.getId().toString());
        assertThat(ids).doesNotContain(stranger.getId().toString());
        assertThat(JsonPath.<List<String>>read(body, "$[*].role")).containsExactly("OWNER", "ADMIN", "MEMBER");
        assertThat(body).doesNotContain("passwordHash").doesNotContain("organizationId").doesNotContain("emailVerified");
    }

    @Test
    void ownerPromotesAMemberAndTheOldSessionsEnd() throws Exception {
        Cookie oldSession = login(member);

        MockHttpServletResponse response = team.changeRole(owner, member.getId(), team.memberTag(owner, member.getId()), "ADMIN");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("ETag")).isEqualTo("\"1\"");
        assertThat(accounts.me(oldSession).getStatus()).isEqualTo(401);
        Cookie newSession = login(member);
        assertThat((String) JsonPath.read(accounts.me(newSession).getContentAsString(), "$.role")).isEqualTo("ADMIN");
        assertThat(users.findByIdAndOrganizationId(member.getId(), organization.getId()).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void ownerDemotesAnAdmin() throws Exception {
        Cookie oldSession = login(admin);

        assertThat(team.changeRole(owner, admin.getId(), team.memberTag(owner, admin.getId()), "MEMBER").getStatus())
                .isEqualTo(200);

        assertThat(accounts.me(oldSession).getStatus()).isEqualTo(401);
        assertThat(roleOf(admin)).isEqualTo(Role.MEMBER);
    }

    @Test
    void onlyTheOwnerChangesRolesAndNeverTheirOwnOrToOwner() throws Exception {
        assertThat(team.changeRole(admin, member.getId(), team.memberTag(owner, member.getId()), "ADMIN").getStatus())
                .isEqualTo(403);
        assertThat(team.changeRole(member, admin.getId(), team.memberTag(owner, admin.getId()), "MEMBER").getStatus())
                .isEqualTo(403);
        assertThat(team.changeRole(owner, owner.getId(), team.memberTag(owner, owner.getId()), "ADMIN").getStatus())
                .isEqualTo(403);
        assertThat(team.changeRole(owner, member.getId(), team.memberTag(owner, member.getId()), "OWNER").getStatus())
                .isEqualTo(400);

        assertThat(roleOf(owner)).isEqualTo(Role.OWNER);
        assertThat(roleOf(admin)).isEqualTo(Role.ADMIN);
        assertThat(roleOf(member)).isEqualTo(Role.MEMBER);
    }

    @Test
    void deactivationEndsSessionsAndBlocksLoginUntilReactivated() throws Exception {
        Cookie oldSession = login(member);

        MockHttpServletResponse deactivated =
                team.memberAction(admin, member.getId(), "deactivate", team.memberTag(owner, member.getId()));

        assertThat(deactivated.getStatus()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(deactivated.getContentAsString(), "$.active")).isFalse();
        assertThat(accounts.me(oldSession).getStatus()).isEqualTo(401);
        assertThat(accounts.login(member.getEmail(), PASSWORD).getStatus()).isEqualTo(401);

        assertThat(team.memberAction(owner, member.getId(), "reactivate", team.memberTag(owner, member.getId())).getStatus())
                .isEqualTo(200);
        assertThat(accounts.me(oldSession).getStatus()).isEqualTo(401);
        assertThat(accounts.login(member.getEmail(), PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void ownerDeactivatesAdminsButAdminsCannotDeactivateAdminsOrTheOwner() throws Exception {
        User otherAdmin = testData.user(organization, PASSWORD, Role.ADMIN);

        assertThat(team.memberAction(admin, otherAdmin.getId(), "deactivate", tag(otherAdmin)).getStatus()).isEqualTo(403);
        assertThat(team.memberAction(admin, owner.getId(), "deactivate", tag(owner)).getStatus()).isEqualTo(403);
        assertThat(team.memberAction(owner, otherAdmin.getId(), "deactivate", tag(otherAdmin)).getStatus()).isEqualTo(200);
        assertThat(team.memberAction(admin, otherAdmin.getId(), "reactivate", tag(otherAdmin)).getStatus()).isEqualTo(403);
    }

    @Test
    void theOwnerCannotBeDeactivatedAndNobodyDeactivatesThemselves() throws Exception {
        assertThat(team.memberAction(owner, owner.getId(), "deactivate", tag(owner)).getStatus()).isEqualTo(403);
        assertThat(team.memberAction(admin, admin.getId(), "deactivate", tag(admin)).getStatus()).isEqualTo(403);
        assertThat(team.memberAction(member, member.getId(), "deactivate", tag(member)).getStatus()).isEqualTo(403);
        assertThat(users.findByIdAndOrganizationId(owner.getId(), organization.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void membersManageNobody() throws Exception {
        User otherMember = testData.user(organization, PASSWORD, Role.MEMBER);

        assertThat(team.memberAction(member, otherMember.getId(), "deactivate", tag(otherMember)).getStatus()).isEqualTo(403);
        assertThat(team.memberAction(member, otherMember.getId(), "reactivate", tag(otherMember)).getStatus()).isEqualTo(403);
        assertThat(team.changeRole(member, otherMember.getId(), tag(otherMember), "ADMIN").getStatus()).isEqualTo(403);
    }

    @Test
    void staleOrMissingVersionChangesNothing() throws Exception {
        String stale = tag(member);
        assertThat(team.changeRole(owner, member.getId(), stale, "ADMIN").getStatus()).isEqualTo(200);

        assertThat(team.memberAction(owner, member.getId(), "deactivate", stale).getStatus()).isEqualTo(412);
        assertThat(team.changeRole(owner, member.getId(), stale, "MEMBER").getStatus()).isEqualTo(412);
        assertThat(team.memberAction(owner, member.getId(), "deactivate", null).getStatus()).isEqualTo(428);
        assertThat(team.changeRole(owner, member.getId(), null, "MEMBER").getStatus()).isEqualTo(428);

        User current = users.findByIdAndOrganizationId(member.getId(), organization.getId()).orElseThrow();
        assertThat(current.getRole()).isEqualTo(Role.ADMIN);
        assertThat(current.isActive()).isTrue();
    }

    @Test
    void requestsCannotCarryOtherFields() throws Exception {
        var response = team.changeRole(owner, member.getId(), tag(member), "ADMIN\",\"organizationId\":\"" + UUID.randomUUID());
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(roleOf(member)).isEqualTo(Role.MEMBER);
    }

    @Test
    void theDatabaseAllowsOneOwnerPerOrganization() {
        assertThatThrownBy(() -> jdbc.update(
                "update users set role = 'OWNER' where id = ?", admin.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("users_one_owner_per_organization_uk");
        assertThat(roleOf(admin)).isEqualTo(Role.ADMIN);
    }

    private Cookie login(User user) throws Exception {
        MockHttpServletResponse response = accounts.login(user.getEmail(), PASSWORD);
        assertThat(response.getStatus()).isEqualTo(204);
        return response.getCookie("SESSION");
    }

    private String tag(User user) throws Exception {
        return team.memberTag(owner, user.getId());
    }

    private Role roleOf(User user) {
        return users.findByIdAndOrganizationId(user.getId(), organization.getId()).orElseThrow().getRole();
    }
}
