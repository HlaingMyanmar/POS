package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentWorkAction;

@Data
public class AssignmentActionRequest {
    private AssignmentWorkAction action;
    private String note;
    private String completedWork;
    private String serviceDetails;
    private String partsDetails;
}
