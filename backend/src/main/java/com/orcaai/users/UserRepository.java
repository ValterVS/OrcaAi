package com.orcaai.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately not a JpaRepository: {@code users} is not filtered by {@code @TenantId}, so there is
 * no unscoped findById, findAll or deleteById. Lookups by id must include the organization.
 */
public interface UserRepository extends Repository<User, UUID> {

    User save(User user);

    User saveAndFlush(User user);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<User> findByOrganizationId(UUID organizationId);

    /**
     * Only for identity flows where the id comes from a server-side record (a consumed one-time
     * token), never from a request.
     */
    @Query("select u from User u where u.id = :id")
    Optional<User> findForIdentityFlow(@Param("id") UUID id);

    /** For authentication and sign-up only, before any organization is known. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
