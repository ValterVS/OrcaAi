package com.orcaai.identity;

import com.orcaai.organizations.Organization;
import com.orcaai.organizations.OrganizationRepository;
import com.orcaai.shared.error.BusinessException;
import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import com.orcaai.users.User;
import com.orcaai.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountService {

    static final String SIGNUP_REJECTED = "Não foi possível criar a conta com os dados informados.";

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    AccountService(OrganizationRepository organizations, UserRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates the organization and its OWNER in one transaction. Sign-up runs without a tenant:
     * organizations and users are not tenant-filtered, and the new organization is taken from the
     * row just created, never from the request.
     */
    @Transactional
    public void signup(SignupRequest request) {
        // Hashing first keeps the response time the same whether or not the email is taken.
        String passwordHash = passwordEncoder.encode(request.password());
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            log.info("Sign-up rejected: email already registered");
            throw new BusinessException(SIGNUP_REJECTED);
        }

        Organization organization = organizations.save(new Organization(request.companyName()));
        try {
            users.saveAndFlush(new User(organization.getId(), request.ownerName(), email, passwordHash, Role.OWNER));
        } catch (DataIntegrityViolationException ex) {
            // Typically a concurrent sign-up with the same email: the unique constraint is the final arbiter.
            log.info("Sign-up rejected by a database constraint");
            throw new BusinessException(SIGNUP_REJECTED);
        }
        log.info("Organization {} created by sign-up", organization.getId());
    }

    @Transactional(readOnly = true)
    public CurrentAccountResponse currentAccount(AuthenticatedUser principal) {
        User user = users.findByIdAndOrganizationId(principal.userId(), principal.organizationId())
                .filter(User::isActive)
                .orElseThrow(() -> new InsufficientAuthenticationException("Account is no longer active"));
        Organization organization = organizations.findById(principal.organizationId())
                .orElseThrow(() -> new InsufficientAuthenticationException("Organization not found"));
        return new CurrentAccountResponse(
                user.getId(), user.getName(), user.getRole(), organization.getId(), organization.getName());
    }
}
