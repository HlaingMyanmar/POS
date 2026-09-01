package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentWorkAction;

import java.time.LocalDateTime;

@Data
public class AssignmentLogDTO {
    private Integer id;
    private AssignmentWorkAction action;
    private String note;
    private String actor;
    private LocalDateTime occurredAt;
}
