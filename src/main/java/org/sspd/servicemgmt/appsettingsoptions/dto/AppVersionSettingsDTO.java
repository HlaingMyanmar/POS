package org.sspd.servicemgmt.appsettingsoptions.dto;

import lombok.Data;

@Data
public class AppVersionSettingsDTO {
    private Integer versionCode;
    private String  versionName;
    private boolean forceUpdate;
    private String  changelog;
    private Integer technicianVersionCode;
    private String  technicianVersionName;
    private boolean technicianForceUpdate;
    private String  technicianChangelog;
}
