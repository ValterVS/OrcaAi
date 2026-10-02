package com.orcaai.team;

import static com.orcaai.team.TeamFixture.PASSWORD;
import static com.orcaai.team.TeamFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.orcaai.organizations.Organization;
import com.orcaai.shared.security.Role;
import com.orcaai.shared.security.SecureTokens;
import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class InvitationIntegrationTest {

    @Autowired
    TeamFixture team;

    @Autowired
    AccountApi accounts;

    @Autowired
    TestData testData;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    Organization organization;
    User owner;
    User admin;
    User member;

    @BeforeEach
    void setUp() {
        organization = testData.organization();
        owner = testData.user(organization, "senha qualquer longa", Role.OWNER);
        admin = testData.user(organization, "senha qualquer longa", Role.ADMIN);
        member = testData.user(organization, "senha qualquer longa", Role.MEMBER);
    }

    @Test
    void ownerInvitesAdminsAndMembersAndTheEmailCarriesOnlyTheLink() throws Exception {
        String adminEmail = uniqueEmail();
        String memberEmail = uniqueEmail();

        MockHttpServletResponse created = team.invite(owner, " " + adminEmail.toUpperCase() + " ", "ADMIN");
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(team.invite(owner, memberEmail, "MEMBER").getStatus()).isEqualTo(201);

        assertThat((String) JsonPath.read(created.getContentAsString(), "$.email")).isEqualTo(adminEmail);
        assertThat(created.getContentAsString()).doesNotContain("token").doesNotContain("organizationId");
        MimeMessage message = mail.awaitMessageTo(adminEmail, 1);
        String text = RecordingMailSender.text(message);
        assertThat(RecordingMailSender.subject(message)).isEqualTo("Você foi convidado para o Orça Aí");
        assertThat(text).contains("Você recebeu um convite para participar de " + organization.getName() + " no Orça Aí.")
                .contains("http://localhost:3000/accept-invite#token=")
                .doesNotContain(organization.getId().toString());
        List<String> pending = JsonPath.read(team.pendingInvitations(admin).getContentAsString(), "$[*].email");
        assertThat(pending).contains(adminEmail, memberEmail);
    }

    @Test
    void adminInvitesMembersButNotAdmins() throws Exception {
        assertThat(team.invite(admin, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(201);
        String refused = uniqueEmail();
        assertThat(team.invite(admin, refused, "ADMIN").getStatus()).isEqualTo(403);
        assertThat(mail.settledMessagesTo(refused)).isEmpty();
    }

    @Test
    void memberCannotInviteNorSeeInvitations() throws Exception {
        assertThat(team.invite(member, uniqueEmail(), "MEMBER").getStatus()).isEqualTo(403);
        assertThat(team.pendingInvitations(member).getStatus()).isEqualTo(403);
    }

    @Test
    void ownerRoleCanNeverBeInvited() throws Exception {
        String email = uniqueEmail();
        assertThat(team.invite(owner, email, "OWNER").getStatus()).isEqualTo(400);
        assertThat(mail.settledMessagesTo(email)).isEmpty();
    }

    @Test
    void addressWithAnAccountAnywhereGetsTheSameGenericRefusal() throws Exception {
        User ofOtherOrganization = testData.user(testData.organization(), "senha qualquer longa", Role.MEMBER);

        MockHttpServletResponse sameOrganization = team.invite(owner, member.getEmail(), "MEMBER");
        MockHttpServletResponse otherOrganization = team.invite(owner, ofOtherOrganization.getEmail(), "MEMBER");

        assertThat(sameOrganization.getStatus()).isEqualTo(otherOrganization.getStatus()).isEqualTo(422);
        assertThat(detail(sameOrganization)).isEqualTo(detail(otherOrganization))
                .isEqualTo("Não foi possível enviar o convite para este endereço.");
        assertThat(mail.settledMessagesTo(ofOtherOrganization.getEmail())).isEmpty();
    }

    @Test
    void acceptingCreatesAVerifiedAccountFromTheInvitationWithoutASession(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        team.invite(owner, email, "ADMIN");
        String token = team.awaitToken(email, 1);

        MockHttpServletResponse accepted = team.accept(token, "  Ana Convidada ", PASSWORD);

        assertThat(accepted.getStatus()).isEqualTo(201);
        assertThat(accepted.getCookie("SESSION")).isNull();
        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getOrganizationId()).isEqualTo(organization.getId());
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getName()).isEqualTo("Ana Convidada");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(mail.settledMessagesTo(email)).hasSize(1);
        var login = accounts.login(email, PASSWORD);
        assertThat(login.getStatus()).isEqualTo(204);
        String me = accounts.me(login.getCookie("SESSION")).getContentAsString();
        assertThat((String) JsonPath.read(me, "$.organizationId")).isEqualTo(organization.getId().toString());
        assertThat((String) JsonPath.read(me, "$.role")).isEqualTo("ADMIN");
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(PASSWORD);
    }

    @Test
    void acceptanceCannotChooseOrganizationRoleOrEmail() throws Exception {
        String email = uniqueEmail();
        team.invite(owner, email, "MEMBER");
        String token = team.awaitToken(email, 1);
        Organization other = testData.organization();

        for (String extra : List.of(
                "\"organizationId\":\"" + other.getId() + "\"", "\"role\":\"OWNER\"",
                "\"email\":\"intruso@example.com\"", "\"emailVerifiedAt\":\"2026-01-01T00:00:00Z\"")) {
            MockHttpServletResponse response = team.acceptRaw(
                    "{\"token\":\"%s\",\"name\":\"Ana\",\"password\":\"%s\",%s}".formatted(token, PASSWORD, extra));
            assertThat(response.getStatus()).as(extra).isEqualTo(400);
        }

        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(team.accept(token, "Ana", PASSWORD).getStatus()).isEqualTo(201);
        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void acceptanceAppliesThePasswordPolicy() throws Exception {
        String email = uniqueEmail();
        team.invite(owner, email, "MEMBER");
        String token = team.awaitToken(email, 1);

        assertThat(team.accept(token, "Ana", "curta").getStatus()).isEqualTo(400);
        assertThat(team.accept(token, "", PASSWORD).getStatus()).isEqualTo(400);
        assertThat(team.accept(token, "Ana", PASSWORD).getStatus()).isEqualTo(201);
    }

    @Test
    void usedExpiredRevokedAndUnknownTokensAreRefused() throws Exception {
        String used = uniqueEmail();
        team.invite(owner, used, "MEMBER");
        String usedToken = team.awaitToken(used, 1);
        team.accept(usedToken, "Ana", PASSWORD);

        String expired = uniqueEmail();
        team.invite(owner, expired, "MEMBER");
        String expiredToken = team.awaitToken(expired, 1);
        jdbc.update("update organization_invitations set expires_at = now() - interval '1 minute' where token_hash = ?",
                SecureTokens.hash(expiredToken));

        String revoked = uniqueEmail();
        String revokedId = team.invitationId(team.invite(owner, revoked, "MEMBER"));
        String revokedToken = team.awaitToken(revoked, 1);
        assertThat(team.invitationAction(owner, revokedId, "revoke").getStatus()).isEqualTo(204);

        assertThat(detail(team.accept(usedToken, "Outra", PASSWORD))).isEqualTo("Este convite já foi utilizado.");
        assertThat(detail(team.accept(expiredToken, "Ana", PASSWORD)))
                .isEqualTo("Este convite expirou. Peça um novo convite a quem convidou você.");
        assertThat(detail(team.accept(revokedToken, "Ana", PASSWORD))).isEqualTo("Este convite não está mais disponível.");
        assertThat(detail(team.accept("d".repeat(43), "Ana", PASSWORD))).isEqualTo("Este convite é inválido.");
        assertThat(users.findByEmail(expired)).isEmpty();
        assertThat(users.findByEmail(revoked)).isEmpty();
    }

    @Test
    void resendIssuesANewTokenAndTheOldOneStopsWorking() throws Exception {
        String email = uniqueEmail();
        String id = team.invitationId(team.invite(owner, email, "MEMBER"));
        String first = team.awaitToken(email, 1);
        allowResend(id);

        assertThat(team.invitationAction(owner, id, "resend").getStatus()).isEqualTo(200);
        String second = team.awaitToken(email, 2);

        assertThat(second).isNotEqualTo(first);
        assertThat(detail(team.accept(first, "Ana", PASSWORD))).isEqualTo("Este convite é inválido.");
        assertThat(team.accept(second, "Ana", PASSWORD).getStatus()).isEqualTo(201);
    }

    @Test
    void resendAndReinviteRespectACooldownAndKeepASinglePendingInvitation() throws Exception {
        String email = uniqueEmail();
        String id = team.invitationId(team.invite(owner, email, "MEMBER"));
        team.awaitToken(email, 1);

        assertThat(detail(team.invitationAction(owner, id, "resend")))
                .isEqualTo("Um convite foi enviado há pouco para este endereço. Aguarde alguns minutos para reenviar.");
        assertThat(team.invite(owner, email, "ADMIN").getStatus()).isEqualTo(422);
        assertThat(mail.settledMessagesTo(email)).hasSize(1);

        allowResend(id);
        MockHttpServletResponse renewed = team.invite(owner, email, "ADMIN");
        assertThat(team.invitationId(renewed)).isEqualTo(id);
        assertThat((String) JsonPath.read(renewed.getContentAsString(), "$.role")).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject(
                "select count(*) from organization_invitations where organization_id = ? and email = ?",
                Integer.class, organization.getId(), email)).isOne();
    }

    @Test
    void revokedInvitationLeavesThePendingList() throws Exception {
        String email = uniqueEmail();
        String id = team.invitationId(team.invite(admin, email, "MEMBER"));

        assertThat(team.invitationAction(admin, id, "revoke").getStatus()).isEqualTo(204);

        List<String> pending = JsonPath.read(team.pendingInvitations(owner).getContentAsString(), "$[*].id");
        assertThat(pending).doesNotContain(id);
        assertThat(detail(team.invitationAction(owner, id, "revoke"))).isEqualTo("Este convite não está mais pendente.");
    }

    @Test
    void adminCannotTouchAnAdminInvitation() throws Exception {
        String email = uniqueEmail();
        String id = team.invitationId(team.invite(owner, email, "ADMIN"));
        allowResend(id);

        assertThat(team.invitationAction(admin, id, "resend").getStatus()).isEqualTo(403);
        assertThat(team.invitationAction(admin, id, "revoke").getStatus()).isEqualTo(403);
        assertThat(team.invite(admin, email, "MEMBER").getStatus()).isEqualTo(403);
        assertThat(team.invitationAction(member, id, "revoke").getStatus()).isEqualTo(403);
    }

    @Test
    void storesOnlyTheHashOfTheToken() throws Exception {
        String email = uniqueEmail();
        team.invite(owner, email, "MEMBER");
        String token = team.awaitToken(email, 1);

        byte[] stored = jdbc.queryForObject(
                "select token_hash from organization_invitations where email = ?", byte[].class, email);
        assertThat(stored).hasSize(32).isEqualTo(SecureTokens.hash(token));
    }

    private void allowResend(String invitationId) {
        jdbc.update("update organization_invitations set last_sent_at = now() - interval '1 hour' where id = ?::uuid",
                invitationId);
    }

    private static String detail(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.detail");
    }
}
