package com.orcaai.identity;

import com.orcaai.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout are handled by Spring Security filters (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AccountService accounts;

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    // The CSRF token is loaded lazily; reading it makes Spring Security issue the XSRF-TOKEN cookie.
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        accounts.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    CurrentAccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.currentAccount(user);
    }
}
