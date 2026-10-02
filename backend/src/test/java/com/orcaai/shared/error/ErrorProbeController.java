package com.orcaai.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for ErrorHandlingIntegrationTest. Top-level so component scanning picks it
 * up; classes nested in test classes are excluded from scanning.
 */
@RestController
class ErrorProbeController {

    record SampleRequest(@NotBlank String name, @NotBlank @Email String email) {
    }

    @PostMapping("/api/test/validation")
    void validate(@Valid @RequestBody SampleRequest request) {
    }

    @GetMapping("/api/test/failure")
    void fail() {
        throw new IllegalStateException("secret-internal-detail");
    }

    @GetMapping("/api/test/missing")
    void missing() {
        throw new ResourceNotFoundException("missing");
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/api/test/owner-only")
    void ownerOnly() {
    }
}
