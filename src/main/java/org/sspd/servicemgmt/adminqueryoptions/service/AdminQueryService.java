package org.sspd.servicemgmt.adminqueryoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.sspd.servicemgmt.adminqueryoptions.config.AdminQueryProperties;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryDefinitionDTO;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryExecuteRequest;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryResultDTO;
import org.sspd.servicemgmt.adminqueryoptions.model.AdminQueryCatalog;
import org.sspd.servicemgmt.adminqueryoptions.model.AdminQueryDefinition;
import org.sspd.servicemgmt.adminqueryoptions.support.SqlQueryValidator;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private static final String PERM_READ = "CAN_ACCESS_ADMIN_QUERY_READ";
    private static final String PERM_WRITE = "CAN_ACCESS_ADMIN_QUERY_WRITE";

    private final JdbcTemplate jdbcTemplate;
    private final AdminQueryProperties adminQueryProperties;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AdminQueryDefinitionDTO> listQueries() {
        ensureEnabled();
        requirePermission(PERM_READ);
        return AdminQueryCatalog.listDtos();
    }

    @Transactional(readOnly = true)
    public AdminQueryResultDTO runQuery(String queryId) {
        ensureEnabled();
        requirePermission(PERM_READ);
        AdminQueryDefinition definition = AdminQueryCatalog.find(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found: " + queryId));

        AdminQueryResultDTO result = executeReadSql(definition.sql());
        result.setQueryId(definition.id());
        result.setQueryName(definition.name());
        result.setMode("READ");

        auditRun("catalog:" + definition.id(), definition.name(), "READ", definition.sql(),
                result.getRowCount(), null, result.getElapsedMs(), result.isTruncated());
        return result;
    }

    @Transactional
    public AdminQueryResultDTO executeCustom(AdminQueryExecuteRequest request) {
        ensureEnabled();
        SqlQueryValidator.Mode mode = SqlQueryValidator.parseMode(request.getMode());
        String sql = SqlQueryValidator.normalize(request.getSql());
        SqlQueryValidator.validate(sql, mode);

        if (mode == SqlQueryValidator.Mode.READ) {
            requirePermission(PERM_READ);
            AdminQueryResultDTO result = executeReadSql(sql);
            result.setQueryId("custom");
            result.setQueryName("Custom SQL");
            result.setMode("READ");
            auditRun("custom", "Custom SQL", "READ", sql,
                    result.getRowCount(), null, result.getElapsedMs(), result.isTruncated());
            return result;
        }

        requirePermission(PERM_WRITE);
        long started = System.currentTimeMillis();
        jdbcTemplate.setQueryTimeout(adminQueryProperties.getTimeoutSeconds());
        int affected = jdbcTemplate.update(sql);
        long elapsedMs = System.currentTimeMillis() - started;

        AdminQueryResultDTO result = new AdminQueryResultDTO();
        result.setQueryId("custom");
        result.setQueryName("Custom SQL (Write)");
        result.setMode("WRITE");
        result.setAffectedRows(affected);
        result.setRowCount(0);
        result.setColumns(List.of());
        result.setRows(List.of());
        result.setElapsedMs(elapsedMs);
        result.setTruncated(false);
        result.setMessage("Affected rows: " + affected);

        auditRun("custom", "Custom SQL (Write)", "WRITE", sql, 0, affected, elapsedMs, false);
        return result;
    }

    private AdminQueryResultDTO executeReadSql(String sql) {
        long started = System.currentTimeMillis();
        jdbcTemplate.setQueryTimeout(adminQueryProperties.getTimeoutSeconds());

        SqlRowSet rowSet = jdbcTemplate.queryForRowSet(sql);
        SqlRowSetMetaData meta = rowSet.getMetaData();
        int columnCount = meta.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<List<Object>> rows = new ArrayList<>();
        int maxRows = adminQueryProperties.getMaxRows();
        boolean truncated = false;
        while (rowSet.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            rows.add(readRow(rowSet, columnCount));
        }

        AdminQueryResultDTO result = new AdminQueryResultDTO();
        result.setColumns(columns);
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setElapsedMs(System.currentTimeMillis() - started);
        result.setTruncated(truncated);
        return result;
    }

    private List<Object> readRow(SqlRowSet rowSet, int columnCount) {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            row.add(rowSet.getObject(i));
        }
        return row;
    }

    private void auditRun(String resourceId, String label, String mode, String sql,
                          int rowCount, Integer affectedRows, long elapsedMs, boolean truncated) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null ? auth.getName() : "unknown";
        String role = auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority()
                : null;
        String sqlPreview = sql.length() > 400 ? sql.substring(0, 400) + "..." : sql;
        String desc = "SQL " + mode + " '" + label + "' (" + resourceId + ")"
                + " rows=" + rowCount
                + (affectedRows != null ? " affected=" + affectedRows : "")
                + " elapsedMs=" + elapsedMs
                + (truncated ? " truncated=true" : "")
                + " sql=" + sqlPreview.replace('\n', ' ');
        auditLogService.log(actor, role, "ACTION", "SqlConsole", resourceId, desc, clientIp(), "Web");
    }

    private void requirePermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        boolean allowed = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(permission) || a.equals("ROLE_ADMINISTRATOR"));
        if (!allowed) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private void ensureEnabled() {
        if (!adminQueryProperties.isEnabled()) {
            throw new AdminQueryDisabledException();
        }
    }
}
