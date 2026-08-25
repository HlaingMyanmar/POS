package org.sspd.servicemgmt.stockoptions.warehouseoptions.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WarehouseDTO {
    private Integer id;
    private String code;
    private String name;
    private String address;
    private Boolean active;
}
