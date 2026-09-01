package org.sspd.servicemgmt.videooptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.videooptions.model.VideoAppType;

@Data
public class VideoPlacementDTO {

    private VideoAppType appType;
    private Integer sortOrder;
    private Boolean featured;
    private Boolean active;
}
