package org.sspd.servicemgmt.videooptions.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoArrangementItemDTO {

    @NotNull
    private Integer videoId;

    private Boolean featured;
}
