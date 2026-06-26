package org.sspd.servicemgmt.dataevent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataEventDTO {
    private String entity;
    private String action;
    private String resourceId;
}
