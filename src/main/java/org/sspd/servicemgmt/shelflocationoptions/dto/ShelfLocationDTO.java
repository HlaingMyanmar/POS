package org.sspd.servicemgmt.shelflocationoptions.dto;

import lombok.Data;

@Data
public class ShelfLocationDTO {
    private Integer id;
    private String code;
    private String label;
    private boolean active;
}
