package org.sspd.servicemgmt.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlywayFailedMigrationRepairTest {

    @Test
    void repairsFailedHistoryThenMigrates() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo failed = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{failed});
        when(failed.getState()).thenReturn(MigrationState.FAILED);

        FlywayMigrationStrategy strategy = new FlywayConfig().flywayMigrationStrategy();
        strategy.migrate(flyway);

        verify(flyway).repair();
        verify(flyway).migrate();
    }

    @Test
    void doesNotRepairWhenHistoryIsClean() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo applied = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(info);
        when(info.all()).thenReturn(new MigrationInfo[]{applied});
        when(applied.getState()).thenReturn(MigrationState.SUCCESS);

        FlywayMigrationStrategy strategy = new FlywayConfig().flywayMigrationStrategy();
        strategy.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }
}
