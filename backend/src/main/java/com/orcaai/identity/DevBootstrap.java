package com.orcaai.identity;

import com.orcaai.organizations.Organization;
import com.orcaai.organizations.OrganizationRepository;
import com.orcaai.shared.security.Role;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates a first organization and owner for local development, until sign-up exists.
 * Credentials come from the environment; nothing is created when they are absent.
 */
@Component
@Profile("dev")
class DevBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrap.class);

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    DevBootstrap(
            OrganizationRepository organizations,
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${orcaai.dev.bootstrap.email:}") String email,
            @Value("${orcaai.dev.bootstrap.password:}") String password) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }
        if (users.existsByEmail(User.normalizeEmail(email))) {
            return;
        }
        Organization organization = organizations.save(new Organization("Organização de desenvolvimento"));
        users.save(new User(organization.getId(), "Responsável", email, passwordEncoder.encode(password), Role.OWNER));
        log.info("Created development organization and owner user");
    }
}
