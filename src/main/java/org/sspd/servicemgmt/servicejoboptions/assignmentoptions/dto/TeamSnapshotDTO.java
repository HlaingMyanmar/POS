package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;

import java.util.List;

@Data
public class TeamSnapshotDTO {
    private Integer serviceJobId;
    private String jobNo;
    private boolean canComplete;
    private String completionBlockReason;
    private boolean leadFinalCheckStatus;
    private String leadFinalCheckedBy;
    private String leadFinalCheckedAt;
    private String leadFinalCheckNote;
    private String finalReturnReason;
    private boolean supervisorApprovalRequired;
    private boolean finalApprovalStatus;
    private List<AssignmentDTO> assignments;
    private List<HandoverDTO> handovers;
    private List<HandoverDTO> myPendingHandovers;
}
