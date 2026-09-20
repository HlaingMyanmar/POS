package org.sspd.servicemgmt.adminqueryoptions.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.adminqueryoptions.config.AdminQueryProperties;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryExecuteRequest;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminQueryServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledFeatureBlocksCatalogCustomReadAndCustomWriteBeforeDatabaseAccess() {
        AdminQueryProperties properties = new AdminQueryProperties();
        AdminQueryService service = new AdminQueryService(jdbcTemplate, properties, auditLogService);
        authenticate("CAN_ACCESS_ADMIN_QUERY_READ", "CAN_ACCESS_ADMIN_QUERY_WRITE");

        assertThrows(AdminQueryDisabledException.class, () -> service.runQuery("recent-sales"));
        assertThrows(AdminQueryDisabledException.class,
                () -> service.executeCustom(request("SELECT 1", "READ")));
        assertThrows(AdminQueryDisabledException.class,
                () -> service.executeCustom(request("UPDATE products SET name = 'x' WHERE id = -1", "WRITE")));

        verifyNoInteractions(jdbcTemplate, auditLogService);
    }

    @Test
    void explicitEnableAllowsPermittedReadAndWriteExecution() {
        AdminQueryProperties properties = new AdminQueryProperties();
        properties.setEnabled(true);
        AdminQueryService service = new AdminQueryService(jdbcTemplate, properties, auditLogService);
        authenticate("CAN_ACCESS_ADMIN_QUERY_READ", "CAN_ACCESS_ADMIN_QUERY_WRITE");

        SqlRowSet rowSet = mock(SqlRowSet.class);
        SqlRowSetMetaData metadata = mock(SqlRowSetMetaData.class);
        when(jdbcTemplate.queryForRowSet("SELECT 1")).thenReturn(rowSet);
        when(rowSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("1");
        when(rowSet.next()).thenReturn(false);
        when(jdbcTemplate.update("UPDATE products SET name = 'x' WHERE id = -1")).thenReturn(0);

        assertEquals(0, service.executeCustom(request("SELECT 1", "READ")).getRowCount());
        assertEquals(0, service.executeCustom(
                request("UPDATE products SET name = 'x' WHERE id = -1", "WRITE")).getAffectedRows());

        verify(jdbcTemplate).queryForRowSet("SELECT 1");
        verify(jdbcTemplate).update("UPDATE products SET name = 'x' WHERE id = -1");
        verify(auditLogService, times(2)).log(
                any(), any(), eq("ACTION"), eq("SqlConsole"), any(), any(), any(), eq("Web"));
    }

    private static AdminQueryExecuteRequest request(String sql, String mode) {
        AdminQueryExecuteRequest request = new AdminQueryExecuteRequest();
        request.setSql(sql);
        request.setMode(mode);
        return request;
    }

    private static void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
