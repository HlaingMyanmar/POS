package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ServiceJobAttachmentDTO {
    private Integer id;
    private String attachmentType;
    private String fileName;
    private String contentType;
    private String dataUrl;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
