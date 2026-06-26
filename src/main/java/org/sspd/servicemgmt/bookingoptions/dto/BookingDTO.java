package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BookingDTO {
    private Integer id;
    private String invoiceNo;
    private Integer customerId;
    private String customerName;
    private String customerPhone;
    private Integer staffId;
    private String staffName;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private String bookingDate;
    private String appointmentDate;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private String remark;
    private Integer paymentAccountId;
    private String transactionNo;

    // Device information
    private String deviceType;
    private String brand;
    private String model;
    private String serialNumber;
    private String color;
    private String accessories;

    // Storage
    private String shelfLocation;

    private List<BookingDeviceInfoDTO> deviceInfos;
    private List<BookingDetailDTO> details;
    private List<BookingDeviceDTO> devices;
}
