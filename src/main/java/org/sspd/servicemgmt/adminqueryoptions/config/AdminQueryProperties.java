package org.sspd.servicemgmt.adminqueryoptions.config;

import lombok.Data;

@Data
public class AdminQueryProperties {
    /** Enable the admin SQL console, including custom read and write statements. */
    private boolean enabled = false;
    private int maxRows = 500;
    private int timeoutSeconds = 30;
}
