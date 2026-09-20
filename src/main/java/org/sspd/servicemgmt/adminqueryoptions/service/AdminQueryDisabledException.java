package org.sspd.servicemgmt.adminqueryoptions.service;

public class AdminQueryDisabledException extends RuntimeException {

    public AdminQueryDisabledException() {
        super("SQL Console is disabled on this server.");
    }
}
