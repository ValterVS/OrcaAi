package com.orcaai.shared.security;

public enum Role {
    OWNER,
    ADMIN,
    MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}
