package com.orcaai.identity;

import com.orcaai.shared.security.SecureTokens;
import static com.orcaai.identity.SignupIntegrationTest.uniqueCompany;
import static com.orcaai.support.AccountApi.PASSWORD;
import static com.orcaai.support.AccountApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

@IntegrationTest
class EmailVerificationIntegrationTest {

    @Autowired
    AccountApi api;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestData testData;

    @Test
    void unverifiedOwnerCannotLogInAndLooksLikeWrongPassword() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);

        MockHttpServletResponse unverified = api.login(email, PASSWORD);
        MockHttpServletResponse wrongPassword = api.login(email, "senha errada qualquer");
        MockHttpServletResponse unknown = api.login(uniqueEmail(), PASSWORD);

        assertThat(unverified.getStatus()).isEqualTo(401);
        assertThat(unverified.getContentAsString())
                .isEqualTo(wrongPassword.getContentAsString())
                .isEqualTo(unknown.getContentAsString());
    }

    @Test
    void validTokenVerifiesTheEmailAndAllowsLogin() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);

        assertThat(api.verify(token).getStatus()).isEqualTo(204);

        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        MockHttpServletResponse login = api.login(email, PASSWORD);
        assertThat(login.getStatus()).isEqualTo(204);
        assertThat(api.me(login.getCookie("SESSION")).getStatus()).isEqualTo(200);
    }

    @Test
    void tokenIsSingleUse() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);
        api.verify(token);

        MockHttpServletResponse reuse = api.verify(token);

        assertThat(reuse.getStatus()).isEqualTo(422);
        assertThat(detail(reuse)).isEqualTo("Este link de confirmação já foi utilizado.");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);
        jdbc.update("update email_verification_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                SecureTokens.hash(token));

        MockHttpServletResponse response = api.verify(token);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(detail(response)).isEqualTo("Este link de confirmação expirou. Solicite um novo envio.");
        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void unknownOrMalformedTokensAreRejected() throws Exception {
        for (String token : new String[] {"a".repeat(43), "not a token", ""}) {
            MockHttpServletResponse response = api.verify(token);
            assertThat(response.getStatus()).isIn(400, 422);
        }
        assertThat(detail(api.verify("a".repeat(43)))).isEqualTo("Este link de confirmação é inválido.");
    }

    @Test
    void storesOnlyTheHashOfTheToken() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);
        User owner = users.findByEmail(email).orElseThrow();

        byte[] stored = jdbc.queryForObject(
                "select token_hash from email_verification_tokens where user_id = ?", byte[].class, owner.getId());

        assertThat(stored).hasSize(32).isEqualTo(SecureTokens.hash(token));
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(token);
    }

    @Test
    void resendIssuesANewTokenAndRetiresThePreviousOne() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String first = mail.awaitToken(email, 1);
        User owner = users.findByEmail(email).orElseThrow();
        jdbc.update("update email_verification_tokens set created_at = now() - interval '1 hour' where user_id = ?",
                owner.getId());

        assertThat(api.resendVerification(email).getStatus()).isEqualTo(202);
        String second = mail.awaitToken(email, 2);

        assertThat(second).isNotEqualTo(first);
        assertThat(detail(api.verify(first))).isEqualTo("Este link de confirmação expirou. Solicite um novo envio.");
        assertThat(api.verify(second).getStatus()).isEqualTo(204);
    }

    @Test
    void resendRespectsTheCooldownWithoutBlockingAnything() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);

        assertThat(api.resendVerification(email).getStatus()).isEqualTo(202);

        assertThat(mail.settledMessagesTo(email)).hasSize(1);
        assertThat(api.verify(token).getStatus()).isEqualTo(204);
    }

    @Test
    void resendAnswersTheSameForUnknownVerifiedAndPendingAddresses() throws Exception {
        String pending = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", pending, PASSWORD);
        String verified = testData.user(testData.organization(), PASSWORD, com.orcaai.shared.security.Role.OWNER)
                .getEmail();

        MockHttpServletResponse forPending = api.resendVerification(pending);
        MockHttpServletResponse forVerified = api.resendVerification(verified);
        MockHttpServletResponse forUnknown = api.resendVerification(uniqueEmail());

        assertThat(forVerified.getStatus()).isEqualTo(forPending.getStatus()).isEqualTo(forUnknown.getStatus())
                .isEqualTo(202);
        assertThat(forVerified.getContentAsString())
                .isEqualTo(forPending.getContentAsString())
                .isEqualTo(forUnknown.getContentAsString());
        assertThat(mail.settledMessagesTo(verified)).isEmpty();
    }

    @Test
    void sessionOfAVerifiedUserKeepsWorking() throws Exception {
        String email = uniqueEmail();
        api.verifiedOwner(uniqueCompany(), email, PASSWORD);
        Cookie session = api.login(email, PASSWORD).getCookie("SESSION");

        assertThat(api.me(session).getStatus()).isEqualTo(200);
    }

    static String detail(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.detail");
    }
}
