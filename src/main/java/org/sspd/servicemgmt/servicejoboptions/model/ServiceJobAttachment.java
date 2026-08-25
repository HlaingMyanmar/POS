package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_job_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id")
    private ServiceJob serviceJob;

    private String attachmentType;
    private String fileName;
    private String contentType;

    @Lob @Column(name = "data_url", columnDefinition = "LONGTEXT")
    private String dataUrl;

    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
