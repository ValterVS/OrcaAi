package com.orcaai.customers;

import com.orcaai.shared.web.EntityTags;
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
 * No DELETE: customers are archived and restored. A single customer is returned with
 * {@code ETag: "<version>"}; edit, archive and restore require it back in If-Match (see EntityTags).
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
    ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        return withTag(service.get(id));
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerDetails details) {
        Customer customer = service.create(details);
        return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
                .eTag(EntityTags.of(customer.getVersion()))
                .body(CustomerResponse.of(customer));
    }

    @PutMapping("/{id}")
    ResponseEntity<CustomerResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody CustomerDetails details) {
        return withTag(service.update(id, EntityTags.expectedVersion(ifMatch), details));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<CustomerResponse> archive(
            @PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return withTag(service.archive(id, EntityTags.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/restore")
    ResponseEntity<CustomerResponse> restore(
            @PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return withTag(service.restore(id, EntityTags.expectedVersion(ifMatch)));
    }

    private static ResponseEntity<CustomerResponse> withTag(Customer customer) {
        return ResponseEntity.ok().eTag(EntityTags.of(customer.getVersion())).body(CustomerResponse.of(customer));
    }
}
