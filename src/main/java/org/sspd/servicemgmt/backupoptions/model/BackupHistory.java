package org.sspd.servicemgmt.backupoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_history", indexes = {
    @Index(name = "idx_backup_history_started_at", columnList = "started_at"),
    @Index(name = "idx_backup_history_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private BackupType backupType;

    @Column(length = 255)
    private String fileName;

    @Column(length = 1000)
    private String filePath;

    private Long fileSize;

    @Builder.Default
    @Column(nullable = false)
    private boolean fileDeleted = false;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private BackupStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}