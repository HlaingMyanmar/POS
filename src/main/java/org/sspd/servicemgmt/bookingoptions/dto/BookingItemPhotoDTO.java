package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingItemPhotoDTO {
    private Integer id;
    private Integer slot;
    private String fileName;
    private String contentType;
    private String dataUrl;
    private String imagePath;
    private String thumbnailPath;
    private LocalDateTime uploadedAt;
}
