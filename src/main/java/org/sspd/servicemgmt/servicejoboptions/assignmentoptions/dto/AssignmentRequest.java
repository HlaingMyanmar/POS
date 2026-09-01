package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;

@Data
public class AssignmentRequest {
    private Integer staffId;
    private AssignmentRole role;
    private String taskDescription;
}
