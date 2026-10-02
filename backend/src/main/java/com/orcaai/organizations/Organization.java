package com.orcaai.organizations;

import com.orcaai.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    protected Organization() {
    }

    public Organization(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
