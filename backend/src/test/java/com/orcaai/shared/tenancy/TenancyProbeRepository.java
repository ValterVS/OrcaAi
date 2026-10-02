package com.orcaai.shared.tenancy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TenancyProbeRepository extends JpaRepository<TenancyProbe, UUID> {

    @Modifying
    @Query("update TenancyProbe p set p.label = :label where p.id = :id")
    int renameInBulk(@Param("id") UUID id, @Param("label") String label);

    @Modifying
    @Query("delete from TenancyProbe p where p.id = :id")
    int deleteInBulk(@Param("id") UUID id);

    // Native SQL is not filtered by @TenantId: the organization filter must be explicit.
    @Query(value = "select * from tenancy_probes where organization_id = :organizationId", nativeQuery = true)
    List<TenancyProbe> findAllNative(@Param("organizationId") UUID organizationId);
}
