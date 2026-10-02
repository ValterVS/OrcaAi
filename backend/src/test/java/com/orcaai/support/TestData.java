package com.orcaai.support;

import com.orcaai.organizations.Organization;
import com.orcaai.organizations.OrganizationRepository;
import com.orcaai.shared.security.Role;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestData {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    TestData(OrganizationRepository organizations, UserRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Organization organization() {
        return organizations.save(new Organization("Org " + UUID.randomUUID()));
    }

    public User user(Organization organization, String password, Role role) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        User user = new User(organization.getId(), "Pessoa de Teste", email, passwordEncoder.encode(password), role);
        user.markEmailVerified(Instant.now());
        return users.save(user);
    }
}
