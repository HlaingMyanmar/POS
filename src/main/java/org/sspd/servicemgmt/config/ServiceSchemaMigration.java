package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/** Idempotent schema upgrades for service jobs and the service catalog. */
@Component
@ConditionalOnProperty(name = "app.schema.java-migrations.enabled", havingValue = "true")
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class ServiceSchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        String database;
        try (var connection = dataSource.getConnection()) {
            database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return;
        }
        if (!database.contains("mysql") && !database.contains("mariadb")) return;

        addColumnIfMissing("services", "warranty_months", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "cost_price", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "duration_minutes", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "description", "TEXT NULL");
        addColumnIfMissing("services", "foc_default", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "tax_rate", "DECIMAL(7,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "skill_required", "VARCHAR(120) NULL");
        addColumnIfMissing("services", "min_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("services", "max_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("services", "commission_percent", "DECIMAL(7,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("services", "supported_device_types", "TEXT NULL");
        addColumnIfMissing("services", "default_required_parts", "TEXT NULL");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS service_item_price_history (
              id INT NOT NULL AUTO_INCREMENT,
              service_item_id INT NOT NULL,
              old_price DECIMAL(15,2) NULL,
              new_price DECIMAL(15,2) NULL,
              old_cost DECIMAL(15,2) NULL,
              new_cost DECIMAL(15,2) NULL,
              changed_by VARCHAR(120) NULL,
              changed_at DATETIME(6) NOT NULL,
              PRIMARY KEY (id),
              KEY idx_service_price_history_item (service_item_id)
            )
            """);


        addColumnIfMissing("service_jobs", "part_requests", "TEXT NULL");
        addColumnIfMissing("service_jobs", "device_type", "VARCHAR(80) NULL");
        addColumnIfMissing("service_jobs", "voided", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("service_jobs", "void_reason", "TEXT NULL");
        addColumnIfMissing("service_jobs", "voided_by", "VARCHAR(120) NULL");
        addColumnIfMissing("service_jobs", "voided_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "estimate_approved", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("service_jobs", "estimate_approved_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "estimate_approved_by", "VARCHAR(120) NULL");
        addColumnIfMissing("service_jobs", "priority", "VARCHAR(20) NOT NULL DEFAULT 'NORMAL'");
        addColumnIfMissing("service_jobs", "helper_staff_id", "INT NULL");
        addColumnIfMissing("service_jobs", "hold_reason", "VARCHAR(500) NULL");
        addColumnIfMissing("service_jobs", "work_started_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "last_notified_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "lead_final_check_status", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("service_jobs", "lead_final_checked_by", "VARCHAR(120) NULL");
        addColumnIfMissing("service_jobs", "lead_final_checked_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "lead_final_check_note", "TEXT NULL");
        addColumnIfMissing("service_jobs", "final_return_reason", "TEXT NULL");
        addColumnIfMissing("service_job_assignment_logs", "completed_work", "TEXT NULL");
        addColumnIfMissing("service_job_assignment_logs", "service_details", "TEXT NULL");
        addColumnIfMissing("service_job_assignment_logs", "parts_details", "TEXT NULL");
        addColumnIfMissing("company_settings", "service_supervisor_approval_required", "BIT NOT NULL DEFAULT 1");
        addColumnIfMissing("company_settings", "service_allow_delivery_with_due", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("service_jobs", "payment_discount_amount", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("service_jobs", "payment_discount_approved_by", "VARCHAR(120) NULL");
        addColumnIfMissing("service_jobs", "payment_discount_approved_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "payment_discount_approval_note", "TEXT NULL");
        addColumnIfMissing("service_jobs", "due_delivery_approved_by", "VARCHAR(120) NULL");
        addColumnIfMissing("service_jobs", "due_delivery_approved_at", "DATETIME(6) NULL");
        addColumnIfMissing("service_jobs", "due_delivery_approval_reason", "TEXT NULL");
        addColumnIfMissing("journal_entries", "status", "VARCHAR(20) NOT NULL DEFAULT 'POSTED'");
        addColumnIfMissing("journal_entries", "reversal_of_id", "INT NULL");
        addColumnIfMissing("journal_entries", "reversed_by", "VARCHAR(120) NULL");
        addColumnIfMissing("journal_entries", "reversed_at", "DATETIME(6) NULL");
        addColumnIfMissing("journal_entries", "reversal_reason", "TEXT NULL");
        addColumnIfMissing("service_job_lines", "confirmation_status", "VARCHAR(30) NOT NULL DEFAULT 'RECOMMENDED'");
        addColumnIfMissing("service_job_lines", "catalog_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "estimated_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "approved_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "billed_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "min_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "max_price", "DECIMAL(15,2) NULL");
        addColumnIfMissing("service_job_lines", "price_change_reason", "VARCHAR(500) NULL");
        addColumnIfMissing("service_job_lines", "price_override_approved", "BIT NOT NULL DEFAULT 0");
        addColumnIfMissing("service_job_lines", "price_override_approved_by", "VARCHAR(120) NULL");
        try {
            jdbcTemplate.execute("""
                UPDATE service_job_lines
                   SET catalog_price = COALESCE(catalog_price, price),
                       estimated_price = COALESCE(estimated_price, price)
                 WHERE price IS NOT NULL
                """);
        } catch (Exception e) {
            log.warn("Could not backfill service_job_lines price columns: {}", e.getMessage());
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS service_job_attachments (
              id INT NOT NULL AUTO_INCREMENT,
              service_job_id INT NOT NULL,
              attachment_type VARCHAR(40) NULL,
              file_name VARCHAR(255) NULL,
              content_type VARCHAR(120) NULL,
              data_url LONGTEXT NULL,
              uploaded_by VARCHAR(120) NULL,
              uploaded_at DATETIME(6) NULL,
              PRIMARY KEY (id),
              KEY idx_sj_att_job (service_job_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS service_job_activities (
              id INT NOT NULL AUTO_INCREMENT,
              service_job_id INT NOT NULL,
              event_type VARCHAR(40) NULL,
              from_status VARCHAR(30) NULL,
              to_status VARCHAR(30) NULL,
              note VARCHAR(1000) NULL,
              actor VARCHAR(120) NULL,
              occurred_at DATETIME(6) NULL,
              PRIMARY KEY (id),
              KEY idx_sj_act_job (service_job_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS service_job_notifications (
              id INT NOT NULL AUTO_INCREMENT,
              service_job_id INT NOT NULL,
              channel VARCHAR(20) NULL,
              note VARCHAR(1000) NULL,
              actor VARCHAR(120) NULL,
              notified_at DATETIME(6) NULL,
              PRIMARY KEY (id),
              KEY idx_sj_note_job (service_job_id)
            )
            """);

        addColumnIfMissing("customer_credit_applications", "service_job_id", "INT NULL");
        try {
            jdbcTemplate.execute("ALTER TABLE customer_credit_applications MODIFY sale_id INT NULL");
        } catch (Exception e) {
            log.warn("Could not make customer_credit_applications.sale_id nullable: {}", e.getMessage());
        }
        createIndexIfMissing("customer_credit_applications", "idx_cca_job", "service_job_id");
        createIndexIfMissing("service_jobs", "idx_sj_priority", "priority");
        createIndexIfMissing("service_jobs", "idx_sj_voided", "voided");
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
