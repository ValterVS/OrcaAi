package com.orcaai.shared.tenancy;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint: tenant-owned JPA access during a request authenticated by a stored session.
 */
@RestController
class TenancyProbeController {

    private final TenancyProbeRepository probes;

    TenancyProbeController(TenancyProbeRepository probes) {
        this.probes = probes;
    }

    @GetMapping("/api/test/tenancy-probes")
    List<String> labels() {
        return probes.findAll().stream().map(TenancyProbe::getLabel).toList();
    }
}
