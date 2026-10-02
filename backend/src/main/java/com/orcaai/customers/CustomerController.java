package com.orcaai.customers;

import com.orcaai.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No DELETE: customers are archived and restored. Saving an edit requires the version the client
 * read, in the If-Match header.
 */
@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<CustomerResponse> list(
            @RequestParam(defaultValue = "ACTIVE") CustomerStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.search(status, q, page, size), CustomerResponse::of);
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.of(service.get(id));
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerDetails details) {
        CustomerResponse created = CustomerResponse.of(service.create(details));
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    CustomerResponse update(
            @PathVariable UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody CustomerDetails details) {
        return CustomerResponse.of(service.update(id, IfMatch.version(ifMatch), details));
    }

    @PostMapping("/{id}/archive")
    CustomerResponse archive(@PathVariable UUID id) {
        return CustomerResponse.of(service.archive(id));
    }

    @PostMapping("/{id}/restore")
    CustomerResponse restore(@PathVariable UUID id) {
        return CustomerResponse.of(service.restore(id));
    }
}
