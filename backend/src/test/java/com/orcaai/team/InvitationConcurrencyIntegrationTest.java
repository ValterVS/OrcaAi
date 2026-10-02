package com.orcaai.team;

import static com.orcaai.team.TeamFixture.PASSWORD;
import static com.orcaai.team.TeamFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.orcaai.shared.error.BusinessException;
import com.orcaai.shared.security.Role;
import com.orcaai.support.IntegrationTest;
import com.orcaai.support.TestData;
import com.orcaai.users.User;
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

@IntegrationTest
class InvitationConcurrencyIntegrationTest {

    private static final int THREADS = 4;

    @Autowired
    InvitationService invitations;

    @Autowired
    TeamFixture team;

    @Autowired
    TestData testData;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentAcceptancesCreateExactlyOneAccount() throws Exception {
        User owner = testData.user(testData.organization(), "senha qualquer longa", Role.OWNER);
        String email = uniqueEmail();
        team.invite(owner, email, "MEMBER");
        String token = team.awaitToken(email, 1);

        CyclicBarrier start = new CyclicBarrier(THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<String> outcomes = new ArrayList<>();
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        invitations.accept(token, "Pessoa " + index, PASSWORD);
                        return "accepted";
                    } catch (BusinessException ex) {
                        return ex.getMessage();
                    }
                }));
            }
            for (Future<String> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(outcomes).filteredOn("accepted"::equals).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.equals("accepted"))
                .containsOnly("Este convite já foi utilizado.");
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isOne();
    }
}
