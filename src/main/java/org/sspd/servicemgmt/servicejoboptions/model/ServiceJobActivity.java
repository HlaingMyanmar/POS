package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id")
    private ServiceJob serviceJob;

    private String eventType;
    private String fromStatus;
    private String toStatus;

    @Column(length = 1000)
    private String note;
    private String actor;
    private LocalDateTime occurredAt;
}
