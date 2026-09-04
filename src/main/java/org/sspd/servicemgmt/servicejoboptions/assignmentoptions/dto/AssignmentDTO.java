package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentApprovalStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentDTO {
    private Integer id;
    private Integer serviceJobId;
    private String jobNo;
    private Integer staffId;
    private String staffName;
    private AssignmentRole role;
    private AssignmentStatus status;
    private AssignmentApprovalStatus approvalStatus;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String taskDescription;
    private String completionNote;
    /** Populated when status is REJECTED (same value as completionNote). */
    private String rejectionReason;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime workStartedAt;
    private LocalDateTime lastActionAt;
    private LocalDateTime completedAt;
    private LocalDateTime endedAt;
    private Long accumulatedMinutes;
    private boolean mine;
    private List<AssignmentLogDTO> logs;
}
