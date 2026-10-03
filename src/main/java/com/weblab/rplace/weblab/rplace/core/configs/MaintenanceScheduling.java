package com.weblab.rplace.weblab.rplace.core.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Runs PlaceMaintenance on its schedule; PLACE_MAINTENANCE_ENABLED=false turns it off (the tests do,
// so that it never removes a row a test is looking at).
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "place.maintenance.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceScheduling {
}
