package com.orcaai.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately not a JpaRepository: {@code users} is not filtered by {@code @TenantId}, so there is
 * no unscoped findById, findAll or deleteById. Lookups by id must include the organization.
 */
public interface UserRepository extends Repository<User, UUID> {

    User save(User user);

    User saveAndFlush(User user);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** For authentication and sign-up only, before any organization is known. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
