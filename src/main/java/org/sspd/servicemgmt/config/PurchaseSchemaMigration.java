package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/** Small idempotent migration for installations created with the old PaymentStatus enum. */
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

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS purchase_budgets (
              id INT NOT NULL AUTO_INCREMENT,
              name VARCHAR(120) NOT NULL,
              date_from DATE NOT NULL,
              date_to DATE NOT NULL,
              category_id INT NULL,
              limit_amount DECIMAL(18,2) NOT NULL,
              enforcement VARCHAR(10) NOT NULL,
              active BIT NOT NULL DEFAULT 1,
              PRIMARY KEY (id),
              CONSTRAINT fk_purchase_budget_category FOREIGN KEY (category_id) REFERENCES categories(id)
            )
            """);
        createIndexIfMissing("purchase_budgets", "idx_purchase_budget_period", "date_from, date_to, active");
        createIndexIfMissing("purchase_budgets", "idx_purchase_budget_category", "category_id");

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
    }

    private void createIndexIfMissing(String table, String index, String columns) {
        Integer found = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema=DATABASE() AND table_name=? AND index_name=?
            """, Integer.class, table, index);
        if (found != null && found == 0) jdbcTemplate.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
    }
}
