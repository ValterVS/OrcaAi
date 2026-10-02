package com.orcaai.identity;

import static com.orcaai.identity.SignupIntegrationTest.uniqueCompany;
import static com.orcaai.support.AccountApi.PASSWORD;
import static com.orcaai.support.AccountApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.shared.error.BusinessException;
import com.orcaai.support.AccountApi;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.users.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Several threads hit the same row at once; the database, not Java checks, decides the winner.
 */
@IntegrationTest
class IdentityConcurrencyIntegrationTest {

    private static final int THREADS = 4;

    @Autowired
    AccountService accounts;

    @Autowired
    PasswordResetService passwordResets;

    @Autowired
    AccountApi api;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void verificationTokenIsConsumedExactlyOnce() throws Exception {
        String email = uniqueEmail();
        api.signup(uniqueCompany(), "Pessoa", email, PASSWORD);
        String token = mail.awaitToken(email, 1);

        List<Outcome> outcomes = concurrently(() -> accounts.verifyEmail(token));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::message).containsOnly("Este link de confirmação já foi utilizado.");
        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void resetTokenIsConsumedExactlyOnce() throws Exception {
        String email = uniqueEmail();
        api.verifiedOwner(uniqueCompany(), email, PASSWORD);
        api.forgotPassword(email);
        String token = mail.awaitToken(email, 2);
        List<String> passwords = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            passwords.add("senha concorrente numero " + i);
        }

        List<Outcome> outcomes = concurrently(index -> passwordResets.resetPassword(token, passwords.get(index)));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        int winner = outcomes.indexOf(outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow());
        assertThat(api.login(email, passwords.get(winner)).getStatus()).isEqualTo(204);
        for (int i = 0; i < THREADS; i++) {
            if (i != winner) {
                assertThat(api.login(email, passwords.get(i)).getStatus()).isEqualTo(401);
            }
        }
    }

    @Test
    void concurrentSignupsForOneAddressCreateOneAccount() throws Exception {
        String email = uniqueEmail();
        String prefix = "Corrida " + java.util.UUID.randomUUID();

        List<Outcome> outcomes = concurrently(index -> accounts.signup(
                new SignupRequest(prefix + " " + index, "Pessoa " + index, email, PASSWORD)));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from organizations where name like ?", Integer.class,
                prefix + "%")).isOne();
    }

    private record Outcome(boolean succeeded, String message) {
    }

    private interface Action {
        void run(int index) throws Exception;
    }

    private List<Outcome> concurrently(Runnable action) throws Exception {
        return concurrently(index -> action.run());
    }

    private List<Outcome> concurrently(Action action) throws Exception {
        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        action.run(index);
                        return new Outcome(true, null);
                    } catch (BusinessException ex) {
                        return new Outcome(false, ex.getMessage());
                    }
                }));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
