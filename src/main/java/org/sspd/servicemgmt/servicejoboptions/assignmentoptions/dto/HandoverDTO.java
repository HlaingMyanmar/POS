package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus;

import java.time.LocalDateTime;

@Data
public class HandoverDTO {
    private Integer id;
    private Integer serviceJobId;
    private String jobNo;
    private Integer fromAssignmentId;
    private Integer fromStaffId;
    private String fromStaffName;
    private Integer toStaffId;
    private String toStaffName;
    private AssignmentRole role;
    private String completedWork;
    private String remainingWork;
    private String diagnosisNote;
    private HandoverStatus status;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String actedBy;
    private LocalDateTime actedAt;
    private String rejectionReason;
    private Integer successorAssignmentId;
    private boolean targetMine;
    private boolean fromMine;
}
