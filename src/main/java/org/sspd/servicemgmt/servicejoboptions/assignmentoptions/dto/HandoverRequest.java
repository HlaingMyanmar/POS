package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;

@Data
public class HandoverRequest {
    private Integer fromAssignmentId;
    private Integer toStaffId;
    private String completedWork;
    private String remainingWork;
    private String diagnosisNote;
}
