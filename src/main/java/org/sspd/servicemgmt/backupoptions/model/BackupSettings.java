package org.sspd.servicemgmt.backupoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "backup_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private BackupFrequency frequency = BackupFrequency.DAILY;

    // DAILY: ignored | WEEKLY: 1=Mon..7=Sun | MONTHLY/YEARLY: day of month
    @Column(name = "day_value")
    private Integer dayValue;

    // YEARLY: month number (1-12)
    @Column(name = "month_value")
    private Integer monthValue;

    @Column(name = "backup_time", nullable = false)
    private LocalTime backupTime = LocalTime.of(2, 0);

    @Column(name = "backup_dir", nullable = false, length = 500)
    private String backupDir = "./backups";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "keep_days")
    private Integer keepDays = 30;

    @Column(name = "mysqldump_path", length = 500)
    private String mysqldumpPath;
}
