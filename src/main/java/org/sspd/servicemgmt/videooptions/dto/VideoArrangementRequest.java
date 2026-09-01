package org.sspd.servicemgmt.videooptions.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VideoArrangementRequest {

    @NotEmpty
    @Valid
    private List<VideoArrangementItemDTO> items;
}
