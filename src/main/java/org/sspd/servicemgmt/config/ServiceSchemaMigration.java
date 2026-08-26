package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Locale;

/** Idempotent schema upgrades for bookings, service jobs, and the service catalog. */
@Component
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

        addColumnIfMissing("bookings", "deposit_amount", "DECIMAL(15,2) NOT NULL DEFAULT 0");
        addColumnIfMissing("bookings", "advance_payment_id", "INT NULL");
        addColumnIfMissing("bookings", "signature_data", "LONGTEXT NULL");
        addColumnIfMissing("booking_devices", "condition_checklist", "TEXT NULL");
        addColumnIfMissing("booking_devices", "part_requests", "TEXT NULL");
        addColumnIfMissing("booking_details", "device_index", "INT NULL");

        addColumnIfMissing("service_jobs", "part_requests", "TEXT NULL");
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

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS booking_attachments (
              id INT NOT NULL AUTO_INCREMENT,
              booking_id INT NOT NULL,
              attachment_type VARCHAR(40) NULL,
              file_name VARCHAR(255) NULL,
              content_type VARCHAR(120) NULL,
              data_url LONGTEXT NULL,
              uploaded_by VARCHAR(120) NULL,
              uploaded_at DATETIME(6) NULL,
              PRIMARY KEY (id),
              KEY idx_booking_att_booking (booking_id)
            )
            """);
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
