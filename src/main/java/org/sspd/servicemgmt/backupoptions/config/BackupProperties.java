package org.sspd.servicemgmt.backupoptions.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "backup")
public class BackupProperties {
    private String rootDirectory = "./Backup";
    private String dailyCron = "0 30 5 * * *";
    private String weeklyCron = "0 40 5 * * SUN";
    private String monthlyCron = "0 50 5 1 * *";
    private int retentionDaily = 7;
    private int retentionWeekly = 4;
    private int retentionMonthly = 12;
    private int retentionManual = 30;
    private int retentionSafety = 7;
    private String timeZone = "Asia/Rangoon";
}