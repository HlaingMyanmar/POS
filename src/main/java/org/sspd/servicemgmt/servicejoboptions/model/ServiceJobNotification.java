package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id")
    private ServiceJob serviceJob;

    private String channel;

    @Column(length = 1000)
    private String note;
    private String actor;
    private LocalDateTime notifiedAt;
}
