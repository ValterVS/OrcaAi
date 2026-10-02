package com.orcaai.customers;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Tenant-filtered by Hibernate ({@code @TenantId}) and by RLS; no query here takes an organization. */
interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
}
