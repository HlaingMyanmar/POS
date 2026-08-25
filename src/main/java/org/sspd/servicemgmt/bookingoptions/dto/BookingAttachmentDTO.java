package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingAttachmentDTO {
    private Integer id;
    private String attachmentType;
    private String fileName;
    private String contentType;
    private String dataUrl;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
