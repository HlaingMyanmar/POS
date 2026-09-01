package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_handovers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobHandover {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id", nullable = false)
    private ServiceJob serviceJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_assignment_id", nullable = false)
    private ServiceJobAssignment fromAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_staff_id", nullable = false)
    private Staff toStaff;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 20)
    private AssignmentRole role;

    @Column(name = "completed_work", columnDefinition = "TEXT")
    private String completedWork;

    @Column(name = "remaining_work", nullable = false, columnDefinition = "TEXT")
    private String remainingWork;

    @Column(name = "diagnosis_note", columnDefinition = "TEXT")
    private String diagnosisNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HandoverStatus status;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;
    private LocalDateTime requestedAt;
    @Column(name = "acted_by", length = 100)
    private String actedBy;
    private LocalDateTime actedAt;
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successor_assignment_id")
    private ServiceJobAssignment successorAssignment;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = HandoverStatus.PENDING;
    }
}
