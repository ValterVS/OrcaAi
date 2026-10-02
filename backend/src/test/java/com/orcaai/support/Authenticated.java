package com.orcaai.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.users.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Authenticates a MockMvc request as an existing user, with the same principal a real login stores
 * in the session. The full login flow is covered by the identity tests.
 */
public final class Authenticated {

    private Authenticated() {
    }

    public static RequestPostProcessor as(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getOrganizationId(), user.getEmail(), null, user.getRole(), true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }
}
