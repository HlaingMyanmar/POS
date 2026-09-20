package org.sspd.servicemgmt.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerpetualInventoryMigrationIdempotencyTest {

    @Test
    void v156DoesNotReclassifyItsOwnCorrectionJournalsOrAddDeltasTwice() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V156__use_perpetual_inventory_for_historical_purchases.sql");
        String sql = Files.readString(migration);

        assertTrue(sql.contains("source.reference_no NOT LIKE 'PERPETUAL-PURCHASE-%'"));
        assertTrue(sql.contains("source.reference_no NOT LIKE 'PERPETUAL-RETURN-%'"));
        assertTrue(sql.contains("GROUP BY year_rows.account_id, year_rows.fiscal_year_num"));
        assertTrue(sql.contains("COALESCE(balance.opening_balance, 0) + restated.journal_net"));
        assertFalse(sql.contains("current_balance = COALESCE(balance.current_balance, 0) + correction.delta"));
    }
}
