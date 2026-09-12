package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.HandoverDTO;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service.ServiceJobTeamService;

@Controller
@RequiredArgsConstructor
public class HandoverWebSocketController {

    private final ServiceJobTeamService handoverService;

    @MessageMapping("/handover.accept")
    @SendTo("/topic/handovers")
    public HandoverDTO onAccept(Integer jobId, Integer handoverId) {
        return handoverService.acceptHandover(jobId, handoverId);
    }

    @MessageMapping("/handover.reject")
    @SendTo("/topic/handovers")
    public HandoverDTO onReject(Integer jobId, Integer handoverId, String reason) {
        var request = new org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.AssignmentDecisionRequest();
        request.setReason(reason);
        return handoverService.rejectHandover(jobId, handoverId, request);
    }

    @MessageMapping("/handover.status")
    @SendTo("/topic/handovers")
    public HandoverDTO onStatusChange(Integer handoverId) {
        return handoverService.findById(handoverId)
                .map(org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover::getStatus)
                .map(status -> {
                    var dto = new HandoverDTO();
                    dto.setStatus(status);
                    return dto;
                })
                .orElse(null);
    }
}