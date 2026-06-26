package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkType;

@Data
public class ReworkRequestDTO {
    private ReworkType reworkType;
    private String problemDesc;
    private Integer assignedStaffId;
}
