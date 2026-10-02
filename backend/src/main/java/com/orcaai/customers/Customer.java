package com.orcaai.customers;

import com.orcaai.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Who the organization works for. No address here on purpose: one customer can have several jobs
 * in different places, so addresses will belong to estimates, proposals and jobs.
 */
@Entity
@Table(name = "customers")
public class Customer extends TenantOwnedEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 4000)
    private String notes;

    private Instant archivedAt;

    protected Customer() {
    }

    Customer(CustomerDetails details) {
        update(details);
    }

    void update(CustomerDetails details) {
        this.name = details.name();
        this.phone = details.phone();
        this.email = details.email();
        this.notes = details.notes();
    }

    void archive(Instant at) {
        if (archivedAt == null) {
            archivedAt = at;
        }
    }

    void restore() {
        archivedAt = null;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
