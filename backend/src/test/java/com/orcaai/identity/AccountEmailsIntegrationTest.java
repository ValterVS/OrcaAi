package com.orcaai.identity;

import static com.orcaai.identity.SignupIntegrationTest.uniqueCompany;
import static com.orcaai.support.AccountApi.PASSWORD;
import static com.orcaai.support.AccountApi.uniqueEmail;
import static com.orcaai.support.CsrfSupport.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.users.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AccountEmailsIntegrationTest {

    @Autowired
    AccountService accounts;

    @Autowired
    AccountApi api;

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    @Qualifier(AccountEmailExecutorConfig.EXECUTOR)
    ThreadPoolTaskExecutor emailExecutor;

    @AfterEach
    void restoreDelivery() {
        mail.failDeliveries(false);
    }

    @Test
    void emailsGoThroughASmallBoundedPool() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        mail.awaitMessageTo(email, 1);

        assertThat(emailExecutor.getMaxPoolSize()).isEqualTo(2);
        assertThat(emailExecutor.getQueueCapacity()).isEqualTo(500);
        assertThat(mail.senderThreads()).isNotEmpty().allMatch(name -> name.startsWith("account-email-"));
    }

    @Test
    void nothingIsSentWhenTheTransactionRollsBack() {
        String email = uniqueEmail();

        transaction.executeWithoutResult(status -> {
            accounts.signup(new SignupRequest(uniqueCompany(), "Pessoa", email, PASSWORD));
            status.setRollbackOnly();
        });

        assertThat(mail.settledMessagesTo(email)).isEmpty();
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test
    void linkUsesTheConfiguredPublicUrlAndAFragment() throws Exception {
        String email = uniqueEmail();

        api.postJson("/api/auth/signup", """
                {"companyName":"Empresa","ownerName":"Pessoa","email":"%s","password":"%s"}"""
                .formatted(email, PASSWORD));

        MimeMessage message = mail.awaitMessageTo(email, 1);
        assertThat(RecordingMailSender.text(message)).contains("http://localhost:3000/verify-email#token=");
        assertThat(message.getFrom()[0].toString()).isEqualTo("nao-responda@orcaai.test");
    }

    @Test
    void forgedHostHeaderDoesNotChangeTheLink() throws Exception {
        String email = uniqueEmail();

        mvc.perform(post("/api/auth/signup")
                .header(HttpHeaders.HOST, "evil.example")
                .header("X-Forwarded-Host", "evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName":"Empresa","ownerName":"Pessoa","email":"%s","password":"%s"}"""
                        .formatted(email, PASSWORD))
                .with(csrfToken(mvc)));

        String text = RecordingMailSender.text(mail.awaitMessageTo(email, 1));
        assertThat(text).contains("http://localhost:3000/verify-email#token=").doesNotContain("evil.example");
    }

    @Test
    void deliveryFailureIsInvisibleToTheClientAndLogsNoDetails(CapturedOutput output) throws Exception {
        mail.failDeliveries(true);
        String email = uniqueEmail();

        MockHttpServletResponse response = api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).doesNotContain("smtp").doesNotContain("Connection");
        awaitLog(output, "Could not send EMAIL_VERIFICATION email");
        assertThat(output.getAll())
                .doesNotContain("smtp.internal.example")
                .doesNotContain(email)
                .contains("(MailSendException)");
        assertThat(users.findByEmail(email)).isPresent();
    }

    @Test
    void tokensAndAddressesNeverReachTheLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String verification = mail.awaitToken(email, 1);
        api.verify(verification);
        api.forgotPassword(email);
        String reset = mail.awaitToken(email, 2);
        api.resetPassword(reset, "nova senha muito longa");

        assertThat(output.getAll())
                .doesNotContain(verification)
                .doesNotContain(reset)
                .doesNotContain(email)
                .doesNotContain(PASSWORD);
    }

    private static void awaitLog(CapturedOutput output, String text) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!output.getAll().contains(text) && Instant.now().isBefore(deadline)) {
            Thread.sleep(25);
        }
        assertThat(output.getAll()).contains(text);
    }
}
