package com.orcaai.identity;

import com.orcaai.shared.security.AuthenticatedUser;
import com.orcaai.shared.security.Role;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout are handled by Spring Security filters (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record CurrentUserResponse(UUID id, UUID organizationId, String email, Role role) {
    }

    // The CSRF token is loaded lazily; reading it makes Spring Security issue the XSRF-TOKEN cookie.
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.userId(), user.organizationId(), user.email(), user.role());
    }
}
