package com.orcaai.customers;

import com.orcaai.shared.error.ResourceNotFoundException;
import com.orcaai.shared.tenancy.TenantContext;
import com.orcaai.shared.web.EntityTags;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organization is never a parameter: every query is filtered by the authenticated organization
 * (Hibernate) and by PostgreSQL RLS. A customer of another organization is simply not found.
 * Logs carry ids only, never names, contact data or notes.
 */
@Service
class CustomerService {

    // Stable order: most recently changed first, id breaks ties so pages never overlap.
    private static final Sort NEWEST_CHANGES_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;

    CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(CustomerStatus status, String term, int page, int size) {
        return customers.findAll(CustomerSearch.matching(status, term), PageRequest.of(page, size, NEWEST_CHANGES_FIRST));
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return find(id);
    }

    @Transactional
    public Customer create(CustomerDetails details) {
        Customer customer = customers.saveAndFlush(new Customer(details));
        log.info("Customer {} created in organization {}", customer.getId(), TenantContext.requireOrganizationId());
        return customer;
    }

    @Transactional
    public Customer update(UUID id, long expectedVersion, CustomerDetails details) {
        Customer customer = find(id);
        EntityTags.requireCurrent(expectedVersion, customer.getVersion());
        customer.update(details);
        customers.flush();
        log.info("Customer {} updated", id);
        return customer;
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public Customer archive(UUID id, long expectedVersion) {
        Customer customer = find(id);
        EntityTags.requireCurrent(expectedVersion, customer.getVersion());
        customer.archive(Instant.now());
        customers.flush();
        log.info("Customer {} archived", id);
        return customer;
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public Customer restore(UUID id, long expectedVersion) {
        Customer customer = find(id);
        EntityTags.requireCurrent(expectedVersion, customer.getVersion());
        customer.restore();
        customers.flush();
        log.info("Customer {} restored", id);
        return customer;
    }

    private Customer find(UUID id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }
}
