package org.sspd.servicemgmt.adminqueryoptions.config;

import lombok.Data;

@Data
public class AdminQueryProperties {
    /** Enable predefined admin query runner (READ-only, catalog-based). */
    private boolean enabled = true;
    private int maxRows = 500;
    private int timeoutSeconds = 30;
}
