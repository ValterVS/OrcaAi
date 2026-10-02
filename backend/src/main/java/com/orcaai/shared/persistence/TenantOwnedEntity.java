package com.orcaai.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base for every business entity owned by an organization.
 *
 * <p>Hibernate fills {@code organization_id} on insert and adds it to every query from the
 * authenticated organization (see {@code OrganizationTenantResolver}). There is intentionally no
 * setter: the owning organization never comes from request data. Native SQL bypasses this filter
 * and must filter by organization explicitly.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    public UUID getOrganizationId() {
        return organizationId;
    }
}
