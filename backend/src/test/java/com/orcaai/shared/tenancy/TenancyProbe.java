package com.orcaai.shared.tenancy;

import com.orcaai.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenancy_probes")
class TenancyProbe extends TenantOwnedEntity {

    @Column(nullable = false, length = 50)
    private String label;

    protected TenancyProbe() {
    }

    TenancyProbe(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }

    void rename(String label) {
        this.label = label;
    }
}
