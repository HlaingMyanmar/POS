package org.sspd.servicemgmt.videooptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.sspd.servicemgmt.videooptions.model.VideoAudience;

import java.time.LocalDateTime;

@Data
public class VideoDTO {

    private Integer id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String provider;

    private String providerVideoId;

    /** Canonical watch URL after save. Also accepted on write if youtubeUrl is empty. */
    private String sourceUrl;

    /** Admin form field: raw YouTube URL on create/update. Echoed as sourceUrl on read. */
    private String youtubeUrl;

    private String thumbnailUrl;

    private String category;

    @NotNull(message = "Target app is required")
    private VideoAudience targetAudience;

    /** Catalog / arrangement position for the requested app. */
    private Integer sortOrder;

    private Boolean featured;

    private java.util.List<VideoPlacementDTO> placements;

    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
