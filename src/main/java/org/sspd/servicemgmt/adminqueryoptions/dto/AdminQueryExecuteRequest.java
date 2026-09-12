package org.sspd.servicemgmt.adminqueryoptions.dto;

import lombok.Data;

@Data
public class AdminQueryExecuteRequest {
    /** Raw SQL — validated server-side by mode. */
    private String sql;
    /** READ = SELECT/SHOW/DESCRIBE/EXPLAIN; WRITE = INSERT/UPDATE/DELETE */
    private String mode = "READ";
}
