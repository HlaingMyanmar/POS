package org.sspd.servicemgmt.backupoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.backupoptions.model.BackupFrequency;

@Data
public class BackupSettingsDTO {
    private Integer id;
    private BackupFrequency frequency;
    private Integer dayValue;
    private Integer monthValue;
    private String backupTime;
    private String backupDir;
    private boolean enabled;
    private Integer keepDays;
    private String mysqldumpPath;
    private String nextRunAt;
    private boolean backupDirExists;
    private boolean backupDirWritable;
    private String lastBackupFile;
    private String lastBackupAt;
    private Long lastBackupSizeBytes;
    private Integer backupCount;
    private boolean dailyEnabled;
    private String dailyTime;
    private boolean weeklyEnabled;
    private Integer weeklyDay;
    private String weeklyTime;
    private boolean monthlyEnabled;
    private Integer monthlyDay;
    private String monthlyTime;
}
