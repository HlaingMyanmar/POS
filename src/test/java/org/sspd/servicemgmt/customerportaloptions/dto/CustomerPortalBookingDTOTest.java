package org.sspd.servicemgmt.customerportaloptions.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerPortalBookingDTOTest {

    @Test
    void omitsInternalRemarkFromCustomerJson() throws Exception {
        BookingDTO internal = new BookingDTO();
        internal.setId(42);
        internal.setBookingNo("BK-42");
        internal.setRemark("Internal staff note");
        internal.setRejectedBy("private-staff-login");
        internal.setRejectionReason("Customer-visible reason");

        CustomerPortalBookingDTO customer = CustomerPortalBookingDTO.from(internal);
        String json = new ObjectMapper().writeValueAsString(customer);

        assertEquals(42, customer.getId());
        assertEquals("BK-42", customer.getBookingNo());
        assertFalse(json.contains("remark"));
        assertFalse(json.contains("Internal staff note"));
        assertFalse(json.contains("rejectedBy"));
        assertFalse(json.contains("private-staff-login"));
        assertEquals("Customer-visible reason", customer.getRejectionReason());
    }
}
