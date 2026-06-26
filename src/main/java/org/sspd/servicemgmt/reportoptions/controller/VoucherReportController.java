package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.reportoptions.service.JasperVoucherService;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class VoucherReportController {

    private final JasperVoucherService jasperVoucherService;

    @GetMapping("/sale/{saleId}")
    public ResponseEntity<byte[]> saleStandard(@PathVariable Integer saleId) throws JRException {
        byte[] pdf = jasperVoucherService.generateSaleStandard(saleId);
        return pdfResponse(pdf, "sale-" + saleId + ".pdf");
    }

    @GetMapping("/sale/{saleId}/pos")
    public ResponseEntity<byte[]> salePos(@PathVariable Integer saleId) throws JRException {
        byte[] pdf = jasperVoucherService.generateSalePos(saleId);
        return pdfResponse(pdf, "sale-pos-" + saleId + ".pdf");
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<byte[]> bookingReceipt(@PathVariable Integer bookingId) throws JRException {
        byte[] pdf = jasperVoucherService.generateBookingReceipt(bookingId);
        return pdfResponse(pdf, "booking-" + bookingId + ".pdf");
    }

    @GetMapping("/service-job/{jobId}")
    public ResponseEntity<byte[]> serviceVoucher(@PathVariable Integer jobId) throws JRException {
        byte[] pdf = jasperVoucherService.generateServiceVoucher(jobId);
        return pdfResponse(pdf, "service-" + jobId + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
