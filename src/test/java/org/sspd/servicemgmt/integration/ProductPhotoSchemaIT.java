package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
class ProductPhotoSchemaIT extends AbstractMysqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void productPhotosTableExistsWithSlotConstraint() {
        Integer latest = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(version, 1) AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertTrue(latest != null && latest >= 115,
                "Expected Flyway version >= 115 (product_photos) but was " + latest);

        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'product_photos'",
                Integer.class);
        assertEquals(1, tables, "product_photos table should exist");

        Integer uniqueSlot = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_schema = DATABASE() AND table_name = 'product_photos' AND constraint_name = 'uk_product_photo_slot'",
                Integer.class);
        assertEquals(1, uniqueSlot, "uk_product_photo_slot unique constraint should exist");
    }
}
