package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_assignment_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobAssignmentLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ServiceJobAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentWorkAction action;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "completed_work", columnDefinition = "TEXT")
    private String completedWork;

    @Column(name = "service_details", columnDefinition = "TEXT")
    private String serviceDetails;

    @Column(name = "parts_details", columnDefinition = "TEXT")
    private String partsDetails;

    @Column(length = 100)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
