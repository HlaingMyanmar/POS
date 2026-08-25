package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/** Small idempotent migration for purchase-related schema upgrades. */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class PurchaseSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        String database;
        try (var connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }
        if (!database.contains("mysql") && !database.contains("mariadb")) return;

        Integer serialStatusColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'product_serials'
                   AND column_name = 'status'
                   AND (data_type = 'enum' OR character_maximum_length < 40)
                """, Integer.class);
        if (serialStatusColumn != null && serialStatusColumn > 0) {
            jdbcTemplate.execute("ALTER TABLE product_serials MODIFY COLUMN status VARCHAR(40) NOT NULL");
            log.info("Migrated product_serials.status to VARCHAR(40)");
        }

        Integer enumColumn = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'purchases'
                   AND column_name = 'payment_status'
                   AND data_type = 'enum'
                """, Integer.class);
        if (enumColumn != null && enumColumn > 0) {
            jdbcTemplate.execute("ALTER TABLE purchases MODIFY COLUMN payment_status VARCHAR(20) NULL");
            log.info("Migrated purchases.payment_status from ENUM to VARCHAR(20)");
        }

        Integer poStatusEnum = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'purchase_orders'
                   AND column_name = 'status'
                   AND data_type = 'enum'
                """, Integer.class);
        if (poStatusEnum != null && poStatusEnum > 0) {
            jdbcTemplate.execute("ALTER TABLE purchase_orders MODIFY COLUMN status VARCHAR(30) NULL");
            log.info("Migrated purchase_orders.status from ENUM to VARCHAR(30)");
        }
        Integer shortPaymentReferenceType = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'payment_transactions'
                   AND column_name = 'reference_type'
                   AND (data_type = 'enum' OR character_maximum_length < 30)
                """, Integer.class);
        if (shortPaymentReferenceType != null && shortPaymentReferenceType > 0) {
            jdbcTemplate.execute("ALTER TABLE payment_transactions MODIFY COLUMN reference_type VARCHAR(30) NULL");
            log.info("Expanded payment_transactions.reference_type for shipping references");
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS purchase_budgets (
              id INT NOT NULL AUTO_INCREMENT,
              name VARCHAR(120) NOT NULL,
              date_from DATE NOT NULL,
              date_to DATE NOT NULL,
              category_id INT NULL,
              supplier_id INT NULL,
              limit_amount DECIMAL(18,2) NOT NULL,
              enforcement VARCHAR(10) NOT NULL,
              active BIT NOT NULL DEFAULT 1,
              PRIMARY KEY (id),
              CONSTRAINT fk_purchase_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
              CONSTRAINT fk_purchase_budget_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
            )
            """);
        addColumnIfMissing("purchase_budgets", "supplier_id", "INT NULL");
        addFkIfMissing("purchase_budgets", "fk_purchase_budget_supplier",
                "ALTER TABLE purchase_budgets ADD CONSTRAINT fk_purchase_budget_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)");
        createIndexIfMissing("purchase_budgets", "idx_purchase_budget_period", "date_from, date_to, active");
        createIndexIfMissing("purchase_budgets", "idx_purchase_budget_category", "category_id");
        createIndexIfMissing("purchase_budgets", "idx_purchase_budget_supplier", "supplier_id");

        jdbcTemplate.execute("""
          CREATE TABLE IF NOT EXISTS stock_lots (
            id INT NOT NULL AUTO_INCREMENT, product_id INT NOT NULL, purchase_detail_id INT NOT NULL,
            batch_number VARCHAR(100), expiry_date DATE, warehouse_name VARCHAR(120),
            received_qty INT NOT NULL, remaining_qty INT NOT NULL, received_at DATETIME(6) NOT NULL,
            status VARCHAR(20) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_stock_lot_purchase_detail(purchase_detail_id),
            CONSTRAINT fk_stock_lot_product FOREIGN KEY(product_id) REFERENCES products(id),
            CONSTRAINT fk_stock_lot_purchase_detail FOREIGN KEY(purchase_detail_id) REFERENCES purchase_details(id)
          )
          """);
        jdbcTemplate.execute("""
          CREATE TABLE IF NOT EXISTS sale_lot_allocations (
            id INT NOT NULL AUTO_INCREMENT, sale_detail_id INT NOT NULL, stock_lot_id INT NOT NULL,
            allocated_qty INT NOT NULL, returned_qty INT NOT NULL DEFAULT 0, PRIMARY KEY(id),
            CONSTRAINT fk_sale_lot_detail FOREIGN KEY(sale_detail_id) REFERENCES sale_details(id),
            CONSTRAINT fk_sale_lot_stock FOREIGN KEY(stock_lot_id) REFERENCES stock_lots(id)
          )
          """);
        jdbcTemplate.execute("""
          CREATE TABLE IF NOT EXISTS sale_return_lot_allocations (
            id INT NOT NULL AUTO_INCREMENT, sale_return_detail_id INT NOT NULL, sale_lot_allocation_id INT NOT NULL,
            qty INT NOT NULL, PRIMARY KEY(id),
            CONSTRAINT fk_return_lot_detail FOREIGN KEY(sale_return_detail_id) REFERENCES sale_return_details(id),
            CONSTRAINT fk_return_lot_sale_alloc FOREIGN KEY(sale_lot_allocation_id) REFERENCES sale_lot_allocations(id)
          )
          """);
        createIndexIfMissing("stock_lots","idx_stock_lot_fefo","product_id, status, expiry_date, received_at");
        createIndexIfMissing("stock_lots","idx_stock_lot_expiry","status, expiry_date, remaining_qty");
        createIndexIfMissing("sale_lot_allocations","idx_sale_lot_detail","sale_detail_id");
        createIndexIfMissing("sale_return_lot_allocations","idx_return_lot_detail","sale_return_detail_id");

        // Supplier payment void columns
        addColumnIfMissing("supplier_payments", "voided", "BIT NULL");
        addColumnIfMissing("supplier_payments", "voided_at", "DATETIME(6) NULL");
        addColumnIfMissing("supplier_payments", "voided_by", "VARCHAR(120) NULL");
        addColumnIfMissing("supplier_payments", "void_reason", "VARCHAR(500) NULL");

        // Multi-level PO approval threshold on company settings
        addColumnIfMissing("company_settings", "po_final_approval_threshold", "DECIMAL(18,2) NULL");

        // Optimistic locking
        addColumnIfMissing("purchases", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_orders", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("products", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("products", "quarantined_qty", "INT NOT NULL DEFAULT 0");

        // Purchase return production workflow. Existing CONFIRMED rows already posted
        // stock/accounting, therefore they are promoted to terminal SETTLED.
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS purchase_return_reasons (
              id INT NOT NULL AUTO_INCREMENT, code VARCHAR(40) NOT NULL, name VARCHAR(120) NOT NULL,
              description VARCHAR(500) NULL, active BIT NOT NULL DEFAULT 1,
              PRIMARY KEY(id), UNIQUE KEY uk_purchase_return_reason_code(code)
            )
            """);
        jdbcTemplate.execute("""
            INSERT IGNORE INTO purchase_return_reasons(code,name,description,active) VALUES
            ('DAMAGED','Damaged','Damaged or defective goods',1),
            ('WRONG_ITEM','Wrong item','Incorrect item supplied',1),
            ('QUALITY','Quality issue','Goods failed quality inspection',1),
            ('OTHER','Other','Legacy or other documented reason',1)
            """);
        addColumnIfMissing("purchase_returns", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_returns", "submitted_by", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "submitted_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "approved_by", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "approved_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "approval_note", "VARCHAR(500) NULL");
        addColumnIfMissing("purchase_returns", "rejected_by", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "rejected_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "rejection_reason", "VARCHAR(500) NULL");
        addColumnIfMissing("purchase_returns", "resolution_type", "VARCHAR(30) NULL");
        addColumnIfMissing("purchase_returns", "rma_number", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "claim_date", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "expected_resolution_date", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "supplier_contact", "VARCHAR(160) NULL");
        addColumnIfMissing("purchase_returns", "claim_status", "VARCHAR(30) NULL");
        addColumnIfMissing("purchase_returns", "replacement_expected_qty", "INT NULL");
        addColumnIfMissing("purchase_returns", "replacement_received_qty", "INT NULL");
        addColumnIfMissing("purchase_returns", "goods_receipt_id", "INT NULL");
        addColumnIfMissing("purchase_returns", "carrier", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "tracking_no", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "dispatched_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "supplier_received_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "delivery_proof", "LONGTEXT NULL");
        addColumnIfMissing("purchase_returns", "shipping_cost_amount", "DECIMAL(18,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_returns", "shipping_payer_responsibility", "VARCHAR(20) NOT NULL DEFAULT 'COMPANY'");
        addColumnIfMissing("purchase_returns", "company_shipping_portion", "DECIMAL(18,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_returns", "supplier_shipping_portion", "DECIMAL(18,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_returns", "shipping_allocation_method", "VARCHAR(20) NOT NULL DEFAULT 'VALUE'");
        addColumnIfMissing("purchase_returns", "shipping_payment_method_id", "INT NULL");
        addColumnIfMissing("purchase_returns", "shipping_transaction_reference", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "shipping_posted_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "settlement_type", "VARCHAR(30) NULL");
        addColumnIfMissing("purchase_returns", "expected_credit_amount", "DECIMAL(18,2) NULL");
        addColumnIfMissing("purchase_returns", "supplier_credit_note_no", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_returns", "supplier_credit_note_amount", "DECIMAL(18,2) NULL");
        addColumnIfMissing("purchase_returns", "credit_variance", "DECIMAL(18,2) NULL");
        addColumnIfMissing("purchase_returns", "credit_variance_reason", "VARCHAR(500) NULL");
        addColumnIfMissing("purchase_returns", "settled_at", "DATETIME(6) NULL");
        addColumnIfMissing("purchase_returns", "settlement_reference", "VARCHAR(120) NULL");
        addColumnIfMissing("purchase_return_details", "reason_id", "INT NULL");
        addColumnIfMissing("purchase_return_details", "allocated_shipping_cost", "DECIMAL(18,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_return_details", "quarantined_qty", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("purchase_return_details", "dispatched_qty", "INT NOT NULL DEFAULT 0");
        // Hibernate ddl-auto runs before this migration. Legacy rows may therefore
        // contain NULL, zero, or another orphan reason id. Repair every orphan
        // before enforcing NOT NULL and the FK.
        jdbcTemplate.execute("""
            UPDATE purchase_return_details d
            LEFT JOIN purchase_return_reasons existing ON existing.id=d.reason_id
            JOIN purchase_return_reasons fallback ON fallback.code='OTHER'
            SET d.reason_id=fallback.id
            WHERE d.reason_id IS NULL OR existing.id IS NULL
            """);
        makeNotNullIfNullable("purchase_return_details", "reason_id", "INT NOT NULL");
        addFkIfMissing("purchase_return_details", "fk_purchase_return_detail_reason",
                "ALTER TABLE purchase_return_details ADD CONSTRAINT fk_purchase_return_detail_reason FOREIGN KEY(reason_id) REFERENCES purchase_return_reasons(id)");
        createIndexIfMissing("purchase_return_details", "idx_pr_detail_reason", "reason_id");
        createIndexIfMissing("purchase_returns", "idx_pr_workflow_status", "status");
        createIndexIfMissing("purchase_returns", "idx_pr_shipping_payment_method", "shipping_payment_method_id");
        addFkIfMissing("purchase_returns", "fk_pr_shipping_payment_method",
                "ALTER TABLE purchase_returns ADD CONSTRAINT fk_pr_shipping_payment_method FOREIGN KEY(shipping_payment_method_id) REFERENCES payment_methods(id)");
        jdbcTemplate.execute("""
            UPDATE purchase_returns
               SET shipping_cost_amount=COALESCE(shipping_cost_amount,0),
                   company_shipping_portion=COALESCE(company_shipping_portion,0),
                   supplier_shipping_portion=COALESCE(supplier_shipping_portion,0),
                   shipping_payer_responsibility=COALESCE(NULLIF(shipping_payer_responsibility,''),'COMPANY'),
                   shipping_allocation_method=COALESCE(NULLIF(shipping_allocation_method,''),'VALUE')
            """);
        jdbcTemplate.execute("UPDATE purchase_returns SET status='SETTLED' WHERE status IS NULL OR status='CONFIRMED'");

        // Warehouse master + transfers
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS warehouses (
              id INT NOT NULL AUTO_INCREMENT,
              code VARCHAR(40) NOT NULL,
              name VARCHAR(120) NOT NULL,
              address VARCHAR(255) NULL,
              active BIT NOT NULL DEFAULT 1,
              PRIMARY KEY (id),
              UNIQUE KEY uk_warehouse_code (code)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS warehouse_transfers (
              id INT NOT NULL AUTO_INCREMENT,
              transfer_no VARCHAR(40) NOT NULL,
              product_id INT NOT NULL,
              from_warehouse_id INT NOT NULL,
              to_warehouse_id INT NOT NULL,
              qty INT NOT NULL,
              transferred_at DATETIME(6) NULL,
              transferred_by VARCHAR(120) NULL,
              remark VARCHAR(255) NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_warehouse_transfer_no (transfer_no),
              CONSTRAINT fk_wt_product FOREIGN KEY (product_id) REFERENCES products(id),
              CONSTRAINT fk_wt_from FOREIGN KEY (from_warehouse_id) REFERENCES warehouses(id),
              CONSTRAINT fk_wt_to FOREIGN KEY (to_warehouse_id) REFERENCES warehouses(id)
            )
            """);
        createIndexIfMissing("warehouse_transfers", "idx_wt_product", "product_id");
        createIndexIfMissing("warehouse_transfers", "idx_wt_from", "from_warehouse_id");
        createIndexIfMissing("warehouse_transfers", "idx_wt_to", "to_warehouse_id");

        // Seed a default Main warehouse if empty
        Integer whCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouses", Integer.class);
        if (whCount != null && whCount == 0) {
            jdbcTemplate.execute("INSERT INTO warehouses (code, name, address, active) VALUES ('MAIN', 'Main', NULL, 1)");
            log.info("Seeded default Main warehouse");
        }
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
        if (found != null && found == 0) jdbcTemplate.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
    }

    private void makeNotNullIfNullable(String table, String column, String definition) {
        Integer found = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema=DATABASE() AND table_name=? AND column_name=? AND is_nullable='YES'
            """, Integer.class, table, column);
        if (found != null && found > 0)
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
    }
}
