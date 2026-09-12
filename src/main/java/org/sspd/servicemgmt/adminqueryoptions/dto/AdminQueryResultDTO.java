package org.sspd.servicemgmt.adminqueryoptions.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminQueryResultDTO {
    private String queryId;
    private String queryName;
    private List<String> columns;
    private List<List<Object>> rows;
    private int rowCount;
    private long elapsedMs;
    private boolean truncated;
    /** WRITE mode affected row count (null for READ/catalog). */
    private Integer affectedRows;
    /** READ or WRITE for custom SQL runs. */
    private String mode;
    /** Short summary for write-only results. */
    private String message;
}
