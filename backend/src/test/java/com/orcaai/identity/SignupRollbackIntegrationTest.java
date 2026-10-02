package com.orcaai.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orcaai.support.IntegrationTest;
import com.orcaai.support.RecordingMailSender;
import com.orcaai.users.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SignupRollbackIntegrationTest {

    @Autowired
    AccountService accounts;

    @Autowired
    UserRepository users;

    @Autowired
    RecordingMailSender mail;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void organizationIsRolledBackWhenOwnerCannotBeStored() {
        String company = "Rollback " + UUID.randomUUID();
        String email = "rollback-" + UUID.randomUUID() + "@example.com";
        // Bypasses request validation so the database rejects the owner after the organization insert.
        SignupRequest request = new SignupRequest(company, "x".repeat(200), email, "uma senha longa o bastante");

        // Not a duplicate address, so the failure is not mistaken for a concurrent sign-up.
        assertThatThrownBy(() -> accounts.signup(request)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("select count(*) from organizations where name = ?", Integer.class, company))
                .isZero();
        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(mail.settledMessagesTo(email)).isEmpty();
    }
}
