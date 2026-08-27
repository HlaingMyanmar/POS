package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/** Idempotent schema upgrades for sales, quotations, returns, and customer payments. */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class SaleSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        String database;
        try (var connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }
        if (!database.contains("mysql") && !database.contains("mariadb")) return;

        addColumnIfMissing("sales", "warehouse_name", "VARCHAR(120) NULL");
        addColumnIfMissing("customer", "advance_balance", "DECIMAL(15,2) NOT NULL DEFAULT 0");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sale_return_reasons (
              id INT NOT NULL AUTO_INCREMENT,
              code VARCHAR(40) NOT NULL,
              name VARCHAR(120) NOT NULL,
              description VARCHAR(500) NULL,
              active BIT NOT NULL DEFAULT 1,
              PRIMARY KEY (id),
              UNIQUE KEY uk_sale_return_reason_code (code)
            )
            """);
        Integer reasonCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sale_return_reasons", Integer.class);
        if (reasonCount != null && reasonCount == 0) {
            jdbcTemplate.execute("""
                INSERT INTO sale_return_reasons (code, name, description, active) VALUES
                ('DEFECT','Defect','Item is damaged or not working',1),
                ('WRONG_ITEM','Wrong item','Wrong product was sold',1),
                ('CUSTOMER_CHANGE','Customer change of mind','Customer no longer wants the item',1),
                ('WARRANTY','Warranty replacement','Returned under warranty',1),
                ('OTHER','Other','Other documented reason',1)
                """);
        }

        addColumnIfMissing("sale_returns", "status", "VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'");
        addColumnIfMissing("sale_returns", "version", "BIGINT NOT NULL DEFAULT 0");
        normalizeVersionColumn("sale_returns");
        addColumnIfMissing("sale_returns", "voided_at", "DATETIME(6) NULL");
        addColumnIfMissing("sale_returns", "void_reason", "TEXT NULL");
        addColumnIfMissing("sale_returns", "voided_by", "VARCHAR(120) NULL");
        addColumnIfMissing("sale_returns", "warehouse_name", "VARCHAR(120) NULL");
        addColumnIfMissing("sale_returns", "settlement_type", "VARCHAR(30) NULL");
        addColumnIfMissing("sale_returns", "credit_note_no", "VARCHAR(50) NULL");
        addColumnIfMissing("sale_returns", "credit_posted_amount", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("sale_return_details", "reason_id", "INT NULL");
        addColumnIfMissing("sale_return_details", "restock", "BIT NOT NULL DEFAULT 1");
        jdbcTemplate.execute("""
            UPDATE sale_return_details d
            LEFT JOIN sale_return_reasons existing ON existing.id=d.reason_id
            JOIN sale_return_reasons fallback ON fallback.code='OTHER'
            SET d.reason_id=fallback.id
            WHERE d.reason_id IS NULL OR existing.id IS NULL
            """);
        addFkIfMissing("sale_return_details", "fk_sale_return_detail_reason",
                "ALTER TABLE sale_return_details ADD CONSTRAINT fk_sale_return_detail_reason FOREIGN KEY(reason_id) REFERENCES sale_return_reasons(id)");
        createIndexIfMissing("sale_return_details", "idx_sr_detail_reason", "reason_id");
        createIndexIfMissing("sale_returns", "idx_sr_status", "status");
        jdbcTemplate.execute("UPDATE sale_returns SET status='COMPLETED' WHERE status IS NULL OR status=''");
        jdbcTemplate.execute("UPDATE sale_returns SET status='VOIDED' WHERE deleted=1 AND (status IS NULL OR status='COMPLETED')");

        addColumnIfMissing("customer_payments", "payment_no", "VARCHAR(50) NULL");
        addColumnIfMissing("customer_payments", "allocated_amount", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("customer_payments", "advance_amount", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("customer_payments", "voided", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("customer_payments", "voided_at", "DATETIME(6) NULL");
        addColumnIfMissing("customer_payments", "voided_by", "VARCHAR(120) NULL");
        addColumnIfMissing("customer_payments", "void_reason", "TEXT NULL");
        jdbcTemplate.execute("""
            UPDATE customer_payments
               SET payment_no=CONCAT('CP-', LPAD(id, 6, '0'))
             WHERE payment_no IS NULL OR payment_no=''
            """);
        createIndexIfMissing("customer_payments", "idx_cp_payment_no", "payment_no");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS customer_payment_allocations (
              id INT NOT NULL AUTO_INCREMENT,
              customer_payment_id INT NOT NULL,
              sale_id INT NOT NULL,
              amount DECIMAL(15,2) NOT NULL,
              PRIMARY KEY (id),
              CONSTRAINT fk_cpa_payment FOREIGN KEY (customer_payment_id) REFERENCES customer_payments(id),
              CONSTRAINT fk_cpa_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
            )
            """);
        createIndexIfMissing("customer_payment_allocations", "idx_cpa_payment", "customer_payment_id");
        createIndexIfMissing("customer_payment_allocations", "idx_cpa_sale", "sale_id");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS customer_credit_applications (
              id INT NOT NULL AUTO_INCREMENT,
              application_no VARCHAR(50) NOT NULL,
              customer_id INT NOT NULL,
              sale_id INT NOT NULL,
              amount DECIMAL(15,2) NOT NULL,
              applied_at DATETIME(6) NOT NULL,
              applied_by VARCHAR(120) NULL,
              reason VARCHAR(500) NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_cca_no (application_no),
              CONSTRAINT fk_cca_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
              CONSTRAINT fk_cca_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
            )
            """);
        createIndexIfMissing("customer_credit_applications", "idx_cca_customer", "customer_id");
        createIndexIfMissing("customer_credit_applications", "idx_cca_sale", "sale_id");
    }

    private void normalizeVersionColumn(String table) {
        jdbcTemplate.execute("UPDATE " + table + " SET version=0 WHERE version IS NULL");
        jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer found = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema=DATABASE() AND table_name=? AND column_name=?
            """, Integer.class, table, column);
        if (found != null && found == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }

    private void addFkIfMissing(String table, String constraint, String ddl) {
        Integer found = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.table_constraints
             WHERE table_schema=DATABASE() AND table_name=? AND constraint_name=? AND constraint_type='FOREIGN KEY'
            """, Integer.class, table, constraint);
        if (found != null && found == 0) {
            try {
                jdbcTemplate.execute(ddl);
                log.info("Added FK {}", constraint);
            } catch (Exception e) {
                log.warn("Could not add FK {}: {}", constraint, e.getMessage());
            }
        }
    }

    private void createIndexIfMissing(String table, String index, String columns) {
        Integer found = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema=DATABASE() AND table_name=? AND index_name=?
            """, Integer.class, table, index);
        if (found != null && found == 0) {
            jdbcTemplate.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
        }
    }
}
