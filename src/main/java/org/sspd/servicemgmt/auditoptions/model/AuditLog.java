package org.sspd.servicemgmt.auditoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_actor",  columnList = "actor"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_module", columnList = "module"),
    @Index(name = "idx_audit_created",columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 60)
    private String module;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(length = 500)
    private String description;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
