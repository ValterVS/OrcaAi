package com.orcaai.identity;

import com.orcaai.shared.security.SecureTokens;
import static com.orcaai.identity.EmailVerificationIntegrationTest.detail;
import static com.orcaai.identity.SignupIntegrationTest.uniqueCompany;
import static com.orcaai.support.AccountApi.PASSWORD;
import static com.orcaai.support.AccountApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

@IntegrationTest
class PasswordResetIntegrationTest {

    private static final String NEW_PASSWORD = "minha nova senha bem longa";

    @Autowired
    AccountApi api;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void requestAnswersTheSameForExistingUnknownAndInactiveAccounts() throws Exception {
        String existing = verifiedOwner();
        String inactive = verifiedOwner();
        jdbc.update("update users set active = false where email = ?", inactive);

        List<MockHttpServletResponse> responses = List.of(
                api.forgotPassword(existing), api.forgotPassword(uniqueEmail()), api.forgotPassword(inactive));

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(202);
        assertThat(responses).extracting(MockHttpServletResponse::getContentAsString).containsOnly(
                responses.getFirst().getContentAsString());
        assertThat(RecordingMailSender.subject(mail.awaitMessageTo(existing, 2)))
                .isEqualTo("Redefinição de senha do Orça Aí");
        assertThat(mail.settledMessagesTo(inactive)).hasSize(1);
    }

    @Test
    void resetChangesPasswordEndsSessionsAndDoesNotLogIn() throws Exception {
        String email = verifiedOwner();
        Cookie laptop = api.login(email, PASSWORD).getCookie("SESSION");
        Cookie phone = api.login(email, PASSWORD).getCookie("SESSION");
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);

        MockHttpServletResponse reset = api.resetPassword(token, NEW_PASSWORD);

        assertThat(reset.getStatus()).isEqualTo(204);
        assertThat(reset.getCookie("SESSION")).isNull();
        assertThat(api.me(laptop).getStatus()).isEqualTo(401);
        assertThat(api.me(phone).getStatus()).isEqualTo(401);
        assertThat(api.login(email, PASSWORD).getStatus()).isEqualTo(401);
        assertThat(api.login(email, NEW_PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void successfulResetSendsANotificationWithoutSecrets() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);

        api.resetPassword(token, NEW_PASSWORD);

        var notice = mail.awaitMessageTo(email, 3);
        String text = RecordingMailSender.text(notice);
        assertThat(RecordingMailSender.subject(notice)).isEqualTo("Sua senha do Orça Aí foi alterada");
        assertThat(text).contains("A senha da sua conta foi alterada recentemente.")
                .contains("Se foi você, nenhuma ação é necessária.")
                .doesNotContain(token).doesNotContain(NEW_PASSWORD).doesNotContain("#token=").doesNotContain("http");
    }

    @Test
    void failedResetSendsNoNotification() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        mail.awaitToken(email, 2);

        api.resetPassword("c".repeat(43), NEW_PASSWORD);

        assertThat(mail.settledMessagesTo(email)).hasSize(2);
    }

    @Test
    void tokenIsSingleUseAndRequestingAnotherRetiresTheFirst() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        String first = mail.awaitToken(email, 2);
        jdbc.update("update password_reset_tokens set created_at = now() - interval '1 hour'"
                + " where user_id = (select id from users where email = ?)", email);
        api.forgotPassword(email);
        String second = mail.awaitToken(email, 3);

        assertThat(detail(api.resetPassword(first, NEW_PASSWORD)))
                .isEqualTo("Este link de redefinição expirou. Solicite uma nova redefinição.");
        assertThat(api.resetPassword(second, NEW_PASSWORD).getStatus()).isEqualTo(204);
        assertThat(detail(api.resetPassword(second, "outra senha totalmente nova")))
                .isEqualTo("Este link de redefinição já foi utilizado.");
        assertThat(api.login(email, NEW_PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void expiredAndInvalidTokensAreRejected() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                SecureTokens.hash(token));

        assertThat(detail(api.resetPassword(token, NEW_PASSWORD)))
                .isEqualTo("Este link de redefinição expirou. Solicite uma nova redefinição.");
        assertThat(detail(api.resetPassword("b".repeat(43), NEW_PASSWORD)))
                .isEqualTo("Este link de redefinição é inválido.");
        assertThat(api.login(email, PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void newPasswordFollowsThePolicyAndKeepsTheTokenUsable() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);

        assertThat(api.resetPassword(token, "curta").getStatus()).isEqualTo(400);

        assertThat(api.resetPassword(token, NEW_PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void unverifiedAccountCanResetAndBecomesVerified() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        mail.awaitMessageTo(email, 1);

        api.forgotPassword(email);
        api.resetPassword(mail.awaitToken(email, 2), NEW_PASSWORD);

        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        assertThat(api.login(email, NEW_PASSWORD).getStatus()).isEqualTo(204);
    }

    @Test
    void inactiveAccountCannotUseAnOutstandingToken() throws Exception {
        String email = verifiedOwner();
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);
        jdbc.update("update users set active = false where email = ?", email);

        MockHttpServletResponse response = api.resetPassword(token, NEW_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(detail(response)).isEqualTo("Este link de redefinição é inválido.");
    }

    private String verifiedOwner() throws Exception {
        String email = uniqueEmail();
        api.verifiedOwner(uniqueCompany(), email, PASSWORD);
        return email;
    }
}
