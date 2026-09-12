package org.sspd.servicemgmt.stockoptions.productoptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductPhotoDTO {
    private Integer id;
    private Integer slot;
    private String fileName;
    private String contentType;
    private String dataUrl;
    private String imagePath;
    private String thumbnailPath;
    private LocalDateTime uploadedAt;
}
