package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerPortalRegisterRequest {
    private String name;
    private String phone;
    private String password;
    private String address;
    private String email;
}
