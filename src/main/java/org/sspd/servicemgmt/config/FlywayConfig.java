package org.sspd.servicemgmt.config;

import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            boolean failed = Arrays.stream(flyway.info().all())
                    .anyMatch(info -> info.getState() == MigrationState.FAILED);
            if (failed) {
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
