package com.orcaai.team;

import static com.orcaai.shared.security.Role.ADMIN;
import static com.orcaai.shared.security.Role.MEMBER;
import static com.orcaai.shared.security.Role.OWNER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamPermissionsTest {

    @Test
    void ownerManagesAdminsAndMembersButNeverAnOwner() {
        assertThat(TeamPermissions.canManage(OWNER, ADMIN)).isTrue();
        assertThat(TeamPermissions.canManage(OWNER, MEMBER)).isTrue();
        assertThat(TeamPermissions.canManage(OWNER, OWNER)).isFalse();
    }

    @Test
    void adminManagesOnlyMembers() {
        assertThat(TeamPermissions.canManage(ADMIN, MEMBER)).isTrue();
        assertThat(TeamPermissions.canManage(ADMIN, ADMIN)).isFalse();
        assertThat(TeamPermissions.canManage(ADMIN, OWNER)).isFalse();
    }

    @Test
    void memberManagesNobody() {
        for (var target : new com.orcaai.shared.security.Role[] {OWNER, ADMIN, MEMBER}) {
            assertThat(TeamPermissions.canManage(MEMBER, target)).isFalse();
        }
    }

    @Test
    void onlyTheOwnerChangesRolesAndOwnerIsNeverInvolved() {
        assertThat(TeamPermissions.canChangeRole(OWNER, MEMBER, ADMIN)).isTrue();
        assertThat(TeamPermissions.canChangeRole(OWNER, ADMIN, MEMBER)).isTrue();
        assertThat(TeamPermissions.canChangeRole(OWNER, MEMBER, OWNER)).isFalse();
        assertThat(TeamPermissions.canChangeRole(OWNER, OWNER, ADMIN)).isFalse();
        assertThat(TeamPermissions.canChangeRole(ADMIN, MEMBER, ADMIN)).isFalse();
        assertThat(TeamPermissions.canChangeRole(ADMIN, MEMBER, MEMBER)).isFalse();
        assertThat(TeamPermissions.canChangeRole(MEMBER, MEMBER, ADMIN)).isFalse();
    }
}
