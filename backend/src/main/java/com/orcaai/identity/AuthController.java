package com.orcaai.identity;

import com.orcaai.identity.AccountRequests.Accepted;
import com.orcaai.identity.AccountRequests.EmailRequest;
import com.orcaai.identity.AccountRequests.ResetPasswordRequest;
import com.orcaai.identity.AccountRequests.TokenRequest;
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
 * Login and logout are handled by Spring Security filters (see {@code SecurityConfig}). Endpoints
 * that receive an email answer 202 with the same message whether or not an account exists.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AccountService accounts;
    private final PasswordResetService passwordResets;

    AuthController(AccountService accounts, PasswordResetService passwordResets) {
        this.accounts = accounts;
        this.passwordResets = passwordResets;
    }

    // The CSRF token is loaded lazily; reading it makes Spring Security issue the XSRF-TOKEN cookie.
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    ResponseEntity<Accepted> signup(@Valid @RequestBody SignupRequest request) {
        accounts.signup(request);
        return accepted(AccountService.SIGNUP_ACCEPTED);
    }

    @PostMapping("/resend-verification")
    ResponseEntity<Accepted> resendVerification(@Valid @RequestBody EmailRequest request) {
        accounts.resendVerification(request.email());
        return accepted(AccountService.RESEND_ACCEPTED);
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request) {
        accounts.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Accepted> forgotPassword(@Valid @RequestBody EmailRequest request) {
        passwordResets.requestReset(request.email());
        return accepted(PasswordResetService.REQUEST_ACCEPTED);
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResets.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentAccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.currentAccount(user);
    }

    private static ResponseEntity<Accepted> accepted(String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new Accepted(message));
    }
}
