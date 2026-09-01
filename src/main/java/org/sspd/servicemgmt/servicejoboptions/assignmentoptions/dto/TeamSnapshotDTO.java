package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto;

import lombok.Data;

import java.util.List;

@Data
public class TeamSnapshotDTO {
    private Integer serviceJobId;
    private String jobNo;
    private boolean canComplete;
    private String completionBlockReason;
    private List<AssignmentDTO> assignments;
    private List<HandoverDTO> handovers;
}
