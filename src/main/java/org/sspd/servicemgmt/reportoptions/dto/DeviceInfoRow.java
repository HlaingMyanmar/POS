package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceInfoRow {
    private String COMPONENT;
    private String DESCRIPTION;
    private String STATUS;
    private String NOTICE;
}
