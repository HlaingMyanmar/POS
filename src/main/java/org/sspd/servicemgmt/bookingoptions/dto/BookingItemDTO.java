package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BookingItemDTO {
    private Integer id;
    private String itemName;
    private String deviceType;
    private String serialNo;
    private String color;
    private String accessories;
    private String problemDesc;
    private String itemCondition;
    private String noticed;
    private Integer convertedJobId;
    private List<BookingItemPhotoDTO> photos = new ArrayList<>();
}
