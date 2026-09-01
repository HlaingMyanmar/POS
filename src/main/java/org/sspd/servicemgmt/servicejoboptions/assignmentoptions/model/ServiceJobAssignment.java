package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id", nullable = false)
    private ServiceJob serviceJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 20)
    private AssignmentRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "approval_status", nullable = false, length = 20)
    private AssignmentApprovalStatus approvalStatus = AssignmentApprovalStatus.APPROVED;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    private LocalDateTime approvedAt;

    @Column(name = "task_description", columnDefinition = "TEXT")
    private String taskDescription;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime workStartedAt;
    private LocalDateTime lastActionAt;
    private LocalDateTime completedAt;
    private LocalDateTime endedAt;

    @Builder.Default
    @Column(name = "accumulated_minutes", nullable = false)
    private Long accumulatedMinutes = 0L;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) assignedAt = LocalDateTime.now();
        if (status == null) status = AssignmentStatus.PENDING;
        if (accumulatedMinutes == null) accumulatedMinutes = 0L;
    }
}
