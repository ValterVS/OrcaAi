package com.orcaai.identity;

import com.orcaai.shared.security.Role;
import java.util.UUID;

record CurrentAccountResponse(
        UUID userId, String userName, Role role, UUID organizationId, String organizationName) {
}
