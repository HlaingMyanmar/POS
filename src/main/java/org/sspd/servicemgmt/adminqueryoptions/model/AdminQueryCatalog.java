package org.sspd.servicemgmt.adminqueryoptions.model;

import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryDefinitionDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminQueryCatalog {

    private static final Map<String, AdminQueryDefinition> QUERIES = new LinkedHashMap<>();

    static {
        register(new AdminQueryDefinition(
                "low-stock",
                "လက်ကျန်နည်းပစ္စည်းများ",
                "reorder level ထက် နည်း သို့မဟုတ် တူသော active ပစ္စည်းများ",
                "ကုန်ပစ္စည်း",
                """
                SELECT p.product_code, p.name, p.stock_qty, p.reorder_level, p.quarantined_qty
                FROM products p
                WHERE (p.archived = 0 OR p.archived IS NULL)
                  AND p.reorder_level IS NOT NULL
                  AND COALESCE(p.stock_qty, 0) <= p.reorder_level
                ORDER BY p.stock_qty ASC, p.name ASC
                """
        ));

        register(new AdminQueryDefinition(
                "zero-stock",
                "လက်ကျန်မရှိပစ္စည်းများ",
                "stock_qty = 0 ဖြစ်သော active ပစ္စည်းများ",
                "ကုန်ပစ္စည်း",
                """
                SELECT p.product_code, p.name, p.stock_qty, p.reorder_level, p.selling_price
                FROM products p
                WHERE (p.archived = 0 OR p.archived IS NULL)
                  AND COALESCE(p.stock_qty, 0) = 0
                ORDER BY p.name ASC
                """
        ));

        register(new AdminQueryDefinition(
                "negative-stock",
                "လက်ကျန်အနှုတ်ပစ္စည်းများ",
                "stock_qty < 0 ဖြစ်သော ပစ္စည်းများ (data issue စစ်ဆေးရန်)",
                "ကုန်ပစ္စည်း",
                """
                SELECT p.product_code, p.name, p.stock_qty, p.quarantined_qty
                FROM products p
                WHERE COALESCE(p.stock_qty, 0) < 0
                ORDER BY p.stock_qty ASC
                """
        ));

        register(new AdminQueryDefinition(
                "overdue-sales",
                "ရောင်းချ အကြွေးကျော်လွန်များ",
                "due_date ကျော်ပြီး due_amount > 0 ရှိသော void မဖြစ်ရောင်းချ",
                "ရောင်းချရေး",
                """
                SELECT s.sale_code, s.sale_date, c.name AS customer_name, c.phone,
                       s.net_amount, s.paid_amount, s.due_amount, s.due_date, s.payment_status
                FROM sales s
                JOIN customer c ON c.id = s.customer_id
                WHERE s.is_voided = 0
                  AND COALESCE(s.due_amount, 0) > 0
                  AND s.due_date IS NOT NULL
                  AND s.due_date < CURDATE()
                ORDER BY s.due_date ASC
                """
        ));

        register(new AdminQueryDefinition(
                "partial-sales",
                "Partial ပေးချေမှု ရောင်းချ",
                "payment_status = Partial ရောင်းချများ",
                "ရောင်းချရေး",
                """
                SELECT s.sale_code, s.sale_date, c.name AS customer_name, c.phone,
                       s.net_amount, s.paid_amount, s.due_amount, s.payment_status
                FROM sales s
                JOIN customer c ON c.id = s.customer_id
                WHERE s.is_voided = 0
                  AND s.payment_status = 'Partial'
                ORDER BY s.sale_date DESC
                """
        ));

        register(new AdminQueryDefinition(
                "jobs-waiting-parts",
                "ပစ္စည်းစောင့် Service Job",
                "status = WAITING_PARTS ဖြစ်သော job များ",
                "ဝန်ဆောင်မှု",
                """
                SELECT sj.job_no, sj.status, sj.hold_reason, sj.received_date,
                       c.name AS customer_name, c.phone,
                       st.name AS assigned_technician
                FROM service_jobs sj
                JOIN customer c ON c.id = sj.customer_id
                LEFT JOIN staff st ON st.id = sj.assigned_staff_id
                WHERE sj.status = 'WAITING_PARTS'
                  AND (sj.voided = 0 OR sj.voided IS NULL)
                ORDER BY sj.received_date DESC
                """
        ));

        register(new AdminQueryDefinition(
                "jobs-estimate-hold",
                "Estimate Hold Service Job",
                "service line confirmation_status = CUSTOMER_HOLD",
                "ဝန်ဆောင်မှု",
                """
                SELECT sj.job_no, sj.status, c.name AS customer_name, c.phone,
                       sv.name AS service_name, sjl.confirmation_status, sjl.estimated_price
                FROM service_job_lines sjl
                JOIN service_jobs sj ON sj.id = sjl.service_job_id
                JOIN customer c ON c.id = sj.customer_id
                JOIN services sv ON sv.id = sjl.service_item_id
                WHERE sjl.confirmation_status = 'CUSTOMER_HOLD'
                  AND (sj.voided = 0 OR sj.voided IS NULL)
                ORDER BY sj.received_date DESC
                """
        ));

        register(new AdminQueryDefinition(
                "jobs-pending-handover",
                "Hand Over စောင့်ဆိုင်း",
                "service_job_handovers status = PENDING",
                "ဝန်ဆောင်မှု",
                """
                SELECT sj.job_no, fs.name AS from_technician, ts.name AS to_technician,
                       h.status, h.requested_at, LEFT(h.remaining_work, 120) AS remaining_work
                FROM service_job_handovers h
                JOIN service_jobs sj ON sj.id = h.service_job_id
                JOIN service_job_assignments fa ON fa.id = h.from_assignment_id
                JOIN staff fs ON fs.id = fa.staff_id
                JOIN staff ts ON ts.id = h.to_staff_id
                WHERE h.status = 'PENDING'
                ORDER BY h.requested_at DESC
                """
        ));

        register(new AdminQueryDefinition(
                "jobs-unassigned",
                "Technician မသတ်မှတ်ရသေး Job",
                "active status ဖြစ်ပြီး assigned_staff_id မရှိ",
                "ဝန်ဆောင်မှု",
                """
                SELECT sj.job_no, sj.status, sj.priority, sj.received_date,
                       c.name AS customer_name, c.phone
                FROM service_jobs sj
                JOIN customer c ON c.id = sj.customer_id
                WHERE sj.assigned_staff_id IS NULL
                  AND (sj.voided = 0 OR sj.voided IS NULL)
                  AND sj.status IN ('RECEIVED', 'ASSIGNED', 'INSPECTING', 'IN_PROGRESS', 'WAITING_PARTS')
                ORDER BY sj.received_date DESC
                """
        ));

        register(new AdminQueryDefinition(
                "jobs-overdue-credit",
                "Service Job အကြွေးကျော်လွန်",
                "due_date ကျော်ပြီး due_amount > 0",
                "ဝန်ဆောင်မှု",
                """
                SELECT sj.job_no, sj.status, c.name AS customer_name, c.phone,
                       sj.net_amount, sj.paid_amount, sj.due_amount, sj.due_date, sj.payment_status
                FROM service_jobs sj
                JOIN customer c ON c.id = sj.customer_id
                WHERE (sj.voided = 0 OR sj.voided IS NULL)
                  AND COALESCE(sj.due_amount, 0) > 0
                  AND sj.due_date IS NOT NULL
                  AND sj.due_date < CURDATE()
                ORDER BY sj.due_date ASC
                """
        ));

        register(new AdminQueryDefinition(
                "customers-credit-hold",
                "Credit Hold ဖောက်သည်များ",
                "credit_hold = true ဖောက်သည်များ",
                "ဖောက်သည်",
                """
                SELECT c.id, c.name, c.phone, c.credit_hold_reason, c.advance_balance
                FROM customer c
                WHERE c.credit_hold = 1
                ORDER BY c.name ASC
                """
        ));

        register(new AdminQueryDefinition(
                "customers-blacklisted",
                "Blacklist ဖောက်သည်များ",
                "blacklisted = true ဖောက်သည်များ",
                "ဖောက်သည်",
                """
                SELECT c.id, c.name, c.phone, c.blacklist_reason
                FROM customer c
                WHERE c.blacklisted = 1
                ORDER BY c.name ASC
                """
        ));

        register(new AdminQueryDefinition(
                "recent-logins",
                "လတ်တလော Login မှတ်တမ်း",
                "ပြီးခဲ့သော 7 ရက် LOGIN audit log",
                "စနစ်",
                """
                SELECT a.actor, a.actor_role, a.ip_address, a.device_type, a.created_at
                FROM audit_logs a
                WHERE a.action = 'LOGIN'
                  AND a.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                ORDER BY a.created_at DESC
                """
        ));

        register(new AdminQueryDefinition(
                "inactive-staff-users",
                "Inactive Staff / User",
                "is_active = false staff သို့မဟုတ် disabled user",
                "စနစ်",
                """
                SELECT s.id AS staff_id, s.name AS staff_name, s.phone, s.role AS staff_role,
                       u.username, u.is_active AS user_active
                FROM staff s
                LEFT JOIN users u ON u.staff_id = s.id
                WHERE s.is_active = 0
                   OR (u.id IS NOT NULL AND u.is_active = 0)
                ORDER BY s.name ASC
                """
        ));

        register(new AdminQueryDefinition(
                "table-row-counts",
                "Table Row Counts",
                "အဓိက table များ row count (information_schema)",
                "စနစ်",
                """
                SELECT table_name, table_rows
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name IN (
                    'products', 'sales', 'sale_details', 'purchases', 'service_jobs',
                    'service_job_lines', 'customer', 'staff', 'users', 'audit_logs',
                    'bookings', 'stock_movements', 'journal_entries'
                  )
                ORDER BY table_rows DESC
                """
        ));
    }

    private AdminQueryCatalog() {}

    private static void register(AdminQueryDefinition definition) {
        QUERIES.put(definition.id(), definition);
    }

    public static List<AdminQueryDefinitionDTO> listDtos() {
        return QUERIES.values().stream().map(AdminQueryDefinition::toDto).toList();
    }

    public static Optional<AdminQueryDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(QUERIES.get(id.trim()));
    }
}
