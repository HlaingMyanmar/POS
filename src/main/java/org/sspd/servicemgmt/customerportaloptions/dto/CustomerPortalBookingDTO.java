package org.sspd.servicemgmt.customerportaloptions.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.beans.BeanUtils;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;

/** Customer-safe booking response that never serializes the staff-only remark. */
public class CustomerPortalBookingDTO extends BookingDTO {

    public static CustomerPortalBookingDTO from(BookingDTO source) {
        CustomerPortalBookingDTO target = new CustomerPortalBookingDTO();
        BeanUtils.copyProperties(source, target, "remark");
        return target;
    }

    @Override
    @JsonIgnore
    public String getRemark() {
        return null;
    }

    @Override
    @JsonIgnore
    public void setRemark(String remark) {
        // Staff-only data must never enter a customer-facing response.
    }

    @Override
    @JsonIgnore
    public String getRejectedBy() {
        return null;
    }

    @Override
    @JsonIgnore
    public void setRejectedBy(String rejectedBy) {
        // Keep the staff identity in the audit record, not in the customer API.
    }
}
