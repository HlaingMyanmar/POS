package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.*;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service.ServiceJobTeamService;

@RestController
@RequestMapping("/api/v1/service-jobs/{jobId}/team")
@RequiredArgsConstructor
public class ServiceJobTeamController {
    private final ServiceJobTeamService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<TeamSnapshotDTO>> getTeam(@PathVariable Integer jobId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service job team", service.snapshot(jobId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<AssignmentDTO>> assign(@PathVariable Integer jobId,
            @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Technician assigned", service.assign(jobId, request)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PutMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<AssignmentDTO>> updateAssignment(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId, @RequestBody AssignmentRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assignment updated",
                service.updateAssignment(jobId, assignmentId, request)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<Void>> cancelAssignment(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId) {
        service.cancelAssignment(jobId, assignmentId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Assignment canceled", null));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_WORK_LOG','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/assignments/{assignmentId}/accept")
    public ResponseEntity<ApiResponse<AssignmentDTO>> acceptAssignment(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assignment accepted",
                service.acceptAssignment(jobId, assignmentId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/assignments/{assignmentId}/approve")
    public ResponseEntity<ApiResponse<AssignmentDTO>> approveAssignment(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assignment approved",
                service.approveAssignment(jobId, assignmentId)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_WORK_LOG','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/assignments/{assignmentId}/reject")
    public ResponseEntity<ApiResponse<AssignmentDTO>> rejectAssignment(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId, @RequestBody(required = false) AssignmentDecisionRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assignment rejected",
                service.rejectAssignment(jobId, assignmentId, request)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_WORK_LOG','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/assignments/{assignmentId}/work")
    public ResponseEntity<ApiResponse<AssignmentDTO>> recordWork(@PathVariable Integer jobId,
            @PathVariable Integer assignmentId, @RequestBody AssignmentActionRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Work log recorded",
                service.recordWork(jobId, assignmentId, request)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_HANDOVER','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/handovers")
    public ResponseEntity<ApiResponse<HandoverDTO>> requestHandover(@PathVariable Integer jobId,
            @RequestBody HandoverRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Hand Over requested",
                service.requestHandover(jobId, request)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_HANDOVER','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/handovers/{handoverId}/accept")
    public ResponseEntity<ApiResponse<HandoverDTO>> acceptHandover(@PathVariable Integer jobId,
            @PathVariable Integer handoverId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Hand Over accepted",
                service.acceptHandover(jobId, handoverId)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_HANDOVER','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/handovers/{handoverId}/reject")
    public ResponseEntity<ApiResponse<HandoverDTO>> rejectHandover(@PathVariable Integer jobId,
            @PathVariable Integer handoverId, @RequestBody(required = false) AssignmentDecisionRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Hand Over rejected",
                service.rejectHandover(jobId, handoverId, request)));
    }
}
