package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.printingoptions.service.HtmlPdfService;
import org.sspd.servicemgmt.printingoptions.service.InvoiceAssemblerService;
import org.sspd.servicemgmt.printingoptions.service.VoucherSettingService;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerPortalInvoiceService {

    private final CustomerOrderRepository orderRepository;
    private final SaleRepository saleRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final InvoiceAssemblerService assembler;
    private final HtmlPdfService pdfService;
    private final VoucherSettingService voucherSettings;

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> orderInvoicePdf(Integer orderId, String paper) {
        var me = CustomerPortalAuth.require();
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(me.getCustomerId())) {
            throw new AccessDeniedException("Not your order");
        }
        if (order.getCompletedSaleId() == null) {
            throw new IllegalStateException("Sale invoice is not ready yet");
        }
        return saleInvoicePdf(order.getCompletedSaleId(), paper, me.getCustomerId());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> purchaseInvoicePdf(Integer saleId, String paper) {
        var me = CustomerPortalAuth.require();
        ServiceJob linkedJob = serviceJobRepository.findFirstBySaleId(saleId).orElse(null);
        if (linkedJob != null
                && linkedJob.getCustomer() != null
                && linkedJob.getCustomer().getId().equals(me.getCustomerId())) {
            return serviceJobInvoicePdf(linkedJob.getId(), paper);
        }
        return saleInvoicePdf(saleId, paper, me.getCustomerId());
    }

    /** Official POS SERVICE_JOB voucher (labor + parts), same template as shop print. */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> serviceJobInvoicePdf(Integer jobId, String paper) {
        var me = CustomerPortalAuth.require();
        ServiceJob job = serviceJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Service job not found"));
        if (job.getCustomer() == null || !job.getCustomer().getId().equals(me.getCustomerId())) {
            throw new AccessDeniedException("Not your job");
        }
        if (Boolean.TRUE.equals(job.getVoided())) {
            throw new IllegalStateException("Service job is voided");
        }
        ServiceJobStatus status = job.getStatus();
        boolean ready = job.getPaymentStatus() != null
                || status == ServiceJobStatus.COMPLETED
                || status == ServiceJobStatus.DELIVERED;
        if (!ready) {
            throw new IllegalStateException("Service job invoice is not ready yet");
        }
        VoucherSetting settings = voucherSettings.findEntity(PrintRequest.DocumentType.SERVICE_JOB).orElse(null);
        PrintRequest req = buildVoucherRequest(PrintRequest.DocumentType.SERVICE_JOB, jobId, paper, settings);
        PrintInvoiceData data = assembler.assemble(req, settings);
        byte[] pdf = pdfService.generatePdf(data, req);
        String filename = "invoice-" + (data.getInvoiceNo() == null ? job.getJobNo() : data.getInvoiceNo()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /** Lightweight money-received confirmation (after APPROVE, before or after fulfill). */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> paymentReceiptPdf(Integer orderId) {
        var me = CustomerPortalAuth.require();
        CustomerOrder order = orderRepository.findByIdWithLines(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(me.getCustomerId())) {
            throw new AccessDeniedException("Not your order");
        }
        String payState = order.getPaymentState() == null ? "" : order.getPaymentState();
        if (!Set.of("PAID", "DEPOSIT_PAID", "FULFILLED").contains(payState)) {
            throw new IllegalStateException("Payment receipt is not ready yet");
        }
        BigDecimal items = order.getItemsTotal() == null ? BigDecimal.ZERO : order.getItemsTotal();
        BigDecimal delivery = order.getDeliveryCharge() == null ? BigDecimal.ZERO : order.getDeliveryCharge();
        BigDecimal total = items.add(delivery);
        BigDecimal paid = "DEPOSIT_PAID".equals(payState)
                && order.getDepositAmount() != null && order.getDepositAmount().signum() > 0
                ? order.getDepositAmount()
                : total;
        String when = order.getPaymentVerifiedAt() == null ? "-"
                : order.getPaymentVerifiedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String handlerNote = "HANDOFF".equals(order.getDeliveryHandler())
                ? "External delivery — shop fee 0 (pay courier separately)"
                : ("Delivery fee " + delivery.toPlainString() + " Ks");
        String body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                <style type="text/css">
                  @page { size: A5; margin: 12mm; }
                  body { font-family: 'Segoe UI', Arial, sans-serif; color: #111; }
                  h1 { font-size: 18pt; margin: 0 0 8px 0; }
                  p { margin: 4px 0; font-size: 11pt; }
                  .amt { font-size: 16pt; font-weight: bold; margin-top: 12px; }
                </style></head>
                <body>
                  <h1>Payment Receipt</h1>
                  <p>Order: %s</p>
                  <p>Verified: %s</p>
                  <p>By: %s</p>
                  <p>Items: %s Ks</p>
                  <p>%s</p>
                  <p class="amt">Received: %s Ks</p>
                  <p>This confirms payment received. Sale invoice is available after fulfill.</p>
                </body></html>
                """.formatted(
                escape(order.getOrderNo()),
                escape(when),
                escape(order.getPaymentVerifiedBy() == null ? "-" : order.getPaymentVerifiedBy()),
                items.toPlainString(),
                escape(handlerNote),
                paid.toPlainString()
        );
        byte[] pdf = xhtmlToPdf(body);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"payment-receipt-" + order.getOrderNo() + ".pdf\"")
                .body(pdf);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static byte[] xhtmlToPdf(String xhtml) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build payment receipt PDF", e);
        }
    }

    private ResponseEntity<byte[]> saleInvoicePdf(Integer saleId, String paper, Integer customerId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));
        if (sale.getCustomer() == null || !sale.getCustomer().getId().equals(customerId)) {
            throw new AccessDeniedException("Not your invoice");
        }
        if (Boolean.TRUE.equals(sale.getVoided())) {
            throw new IllegalStateException("Sale is voided");
        }
        VoucherSetting settings = voucherSettings.findEntity(PrintRequest.DocumentType.SALE).orElse(null);
        PrintRequest req = buildVoucherRequest(PrintRequest.DocumentType.SALE, saleId, paper, settings);
        PrintInvoiceData data = assembler.assemble(req, settings);
        byte[] pdf = pdfService.generatePdf(data, req);
        String filename = "invoice-" + (data.getInvoiceNo() == null ? saleId : data.getInvoiceNo()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private PrintRequest buildVoucherRequest(
            PrintRequest.DocumentType type, Integer documentId, String paperOverride, VoucherSetting s) {
        PrintRequest r = new PrintRequest();
        r.setDocumentType(type);
        r.setDocumentId(documentId);
        r.setCopyType("CUSTOMER");
        r.setPaperSize(paperOverride != null ? paperOverride
                : (s != null ? s.getPaperSize() : "A4"));
        r.setShowLogo(s != null && s.getShowLogo() != null ? s.getShowLogo() : true);
        r.setShowSerial(s != null && s.getShowSerial() != null ? s.getShowSerial() : true);
        r.setShowColRowNo(s != null && s.getShowColRowNo() != null ? s.getShowColRowNo() : true);
        r.setShowColItem(s != null && s.getShowColItem() != null ? s.getShowColItem() : true);
        r.setShowColQty(s != null && s.getShowColQty() != null ? s.getShowColQty() : true);
        r.setShowColUnitPrice(s != null && s.getShowColUnitPrice() != null ? s.getShowColUnitPrice() : true);
        r.setShowColAmount(s != null && s.getShowColAmount() != null ? s.getShowColAmount() : true);
        r.setShowColWarranty(s != null && s.getShowColWarranty() != null ? s.getShowColWarranty() : true);
        r.setShowColLineDiscount(s != null && s.getShowColLineDiscount() != null ? s.getShowColLineDiscount() : true);
        r.setShowPaymentHistory(s != null && s.getShowPaymentHistory() != null ? s.getShowPaymentHistory() : true);
        r.setShowSignatures(s != null && s.getShowSignatures() != null ? s.getShowSignatures() : false);
        r.setShowQrCode(s != null && s.getShowQrCode() != null ? s.getShowQrCode() : false);
        r.setSign1Label(s != null && s.getSign1Label() != null ? s.getSign1Label() : "Prepared By");
        r.setSign2Label(s != null && s.getSign2Label() != null ? s.getSign2Label() : "Received By");
        return r;
    }
}
