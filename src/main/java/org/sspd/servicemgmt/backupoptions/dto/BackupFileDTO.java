package org.sspd.servicemgmt.backupoptions.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BackupFileDTO {
    private String fileName;
    private long sizeBytes;
    private String modifiedAt;
    private long ageDays;
}
