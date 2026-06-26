package org.sspd.servicemgmt.printingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.printingoptions.service.HtmlPdfService;
import org.sspd.servicemgmt.printingoptions.service.InvoiceAssemblerService;
import org.sspd.servicemgmt.printingoptions.service.VoucherSettingService;

/**
 * REST API for the enterprise print system.
 *
 * <pre>
 * POST /api/v1/print/pdf      → application/pdf (Flying Saucer, inline)
 * POST /api/v1/print/preview  → text/html       (browser print preview)
 * GET  /api/v1/print/pdf/sale/{id}?paper=A4
 * GET  /api/v1/print/pdf/service-job/{id}?paper=A5
 * GET  /api/v1/print/pdf/booking/{id}?paper=A4
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/print")
@RequiredArgsConstructor
public class InvoicePrintController {

    private static final MediaType TEXT_HTML_UTF8 = MediaType.valueOf("text/html;charset=UTF-8");

    private final InvoiceAssemblerService assembler;
    private final HtmlPdfService          pdfService;
    private final VoucherSettingService   voucherSettings;

    // ── POST endpoints (full request body control) ─────────────────────────

    /**
     * Generates and streams a PDF.
     * Content-Disposition: inline → opens in browser PDF viewer / print dialog.
     */
    @SuppressWarnings("null")
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdf(@RequestBody PrintRequest req) {
        VoucherSetting s = voucherSettings.findEntity(req.getDocumentType()).orElse(null);
        applySettingToRequest(req, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        byte[] pdf = pdfService.generatePdf(data, req);

        String filename = "invoice-" + data.getInvoiceNo() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /**
     * Returns rendered HTML suitable for injecting into an iframe preview.
     */
    @SuppressWarnings("null")
    @PostMapping("/preview")
    public ResponseEntity<String> generateHtmlPreview(@RequestBody PrintRequest req) {
        VoucherSetting s = voucherSettings.findEntity(req.getDocumentType()).orElse(null);
        applySettingToRequest(req, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        String html = pdfService.generateHtmlPreview(data, req);
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(html);
    }

    // ── GET shortcuts (convenient for direct browser/link access) ──────────

    @GetMapping("/pdf/sale/{id}")
    public ResponseEntity<byte[]> salePdf(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {

        VoucherSetting s = voucherSettings.findEntity(PrintRequest.DocumentType.SALE).orElse(null);
        PrintRequest req = buildReqFromSetting(PrintRequest.DocumentType.SALE, id, paper, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        return pdfResponse(data, req);
    }

    @GetMapping("/pdf/service-job/{id}")
    public ResponseEntity<byte[]> serviceJobPdf(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {

        VoucherSetting s = voucherSettings.findEntity(PrintRequest.DocumentType.SERVICE_JOB).orElse(null);
        PrintRequest req = buildReqFromSetting(PrintRequest.DocumentType.SERVICE_JOB, id, paper, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        return pdfResponse(data, req);
    }

    @GetMapping("/pdf/booking/{id}")
    public ResponseEntity<byte[]> bookingPdf(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {

        VoucherSetting s = voucherSettings.findEntity(PrintRequest.DocumentType.BOOKING).orElse(null);
        PrintRequest req = buildReqFromSetting(PrintRequest.DocumentType.BOOKING, id, paper, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        return pdfResponse(data, req);
    }

    @GetMapping("/preview/sale/{id}")
    public ResponseEntity<String> salePreview(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {

        VoucherSetting s = voucherSettings.findEntity(PrintRequest.DocumentType.SALE).orElse(null);
        PrintRequest req = buildReqFromSetting(PrintRequest.DocumentType.SALE, id, paper, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        return previewResponse(data, req);
    }

    @GetMapping("/preview/service-job/{id}")
    public ResponseEntity<String> serviceJobPreview(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {

        VoucherSetting s = voucherSettings.findEntity(PrintRequest.DocumentType.SERVICE_JOB).orElse(null);
        PrintRequest req = buildReqFromSetting(PrintRequest.DocumentType.SERVICE_JOB, id, paper, s);
        PrintInvoiceData data = assembler.assemble(req, s);
        return previewResponse(data, req);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Applies DB voucher settings to an incoming POST request.
     *  Paper size: request wins (user can switch in preview toolbar).
     *  Display toggles and labels: DB wins (admin-configured). */
    private void applySettingToRequest(PrintRequest req, VoucherSetting s) {
        if (s == null) return;
        if (req.getPaperSize() == null || req.getPaperSize().isBlank()) {
            req.setPaperSize(s.getPaperSize());
        }
        if (s.getShowLogo()           != null) req.setShowLogo(s.getShowLogo());
        if (s.getShowSerial()         != null) req.setShowSerial(s.getShowSerial());
        if (s.getShowPaymentHistory() != null) req.setShowPaymentHistory(s.getShowPaymentHistory());
        if (s.getShowSignatures()     != null) req.setShowSignatures(s.getShowSignatures());
        if (s.getShowQrCode()         != null) req.setShowQrCode(s.getShowQrCode());
        if (s.getSign1Label()             != null) req.setSign1Label(s.getSign1Label());
        if (s.getSign2Label()             != null) req.setSign2Label(s.getSign2Label());
        if (s.getHeaderFontFamily()       != null) req.setHeaderFontFamily(s.getHeaderFontFamily());
        if (s.getHeaderFontSizePx()       != null) req.setHeaderFontSizePx(s.getHeaderFontSizePx());
        if (s.getInfoFontFamily()         != null) req.setInfoFontFamily(s.getInfoFontFamily());
        if (s.getInfoFontSizePx()         != null) req.setInfoFontSizePx(s.getInfoFontSizePx());
        if (s.getTableHeaderFontFamily()  != null) req.setTableHeaderFontFamily(s.getTableHeaderFontFamily());
        if (s.getTableHeaderFontSizePx()  != null) req.setTableHeaderFontSizePx(s.getTableHeaderFontSizePx());
        if (s.getTableDataFontFamily()    != null) req.setTableDataFontFamily(s.getTableDataFontFamily());
        if (s.getTableDataFontSizePx()    != null) req.setTableDataFontSizePx(s.getTableDataFontSizePx());
        if (s.getFooterFontFamily()       != null) req.setFooterFontFamily(s.getFooterFontFamily());
        if (s.getFooterFontSizePx()       != null) req.setFooterFontSizePx(s.getFooterFontSizePx());
        if (s.getNoticeFontFamily()       != null) req.setNoticeFontFamily(s.getNoticeFontFamily());
        if (s.getNoticeFontSizePx()       != null) req.setNoticeFontSizePx(s.getNoticeFontSizePx());
    }

    private PrintRequest buildReqFromSetting(
            PrintRequest.DocumentType type, Integer id, String paperOverride, VoucherSetting s) {
        PrintRequest r = new PrintRequest();
        r.setDocumentType(type);
        r.setDocumentId(id);
        r.setPaperSize(paperOverride != null ? paperOverride
                : (s != null ? s.getPaperSize() : "A4"));
        r.setShowLogo(s != null && s.getShowLogo() != null ? s.getShowLogo() : true);
        r.setShowSerial(s != null && s.getShowSerial() != null ? s.getShowSerial() : true);
        r.setShowPaymentHistory(s != null && s.getShowPaymentHistory() != null ? s.getShowPaymentHistory() : true);
        r.setShowSignatures(s != null && s.getShowSignatures() != null ? s.getShowSignatures() : false);
        r.setShowQrCode(s != null && s.getShowQrCode() != null ? s.getShowQrCode() : false);
        r.setSign1Label(s != null && s.getSign1Label() != null ? s.getSign1Label() : "Prepared By");
        r.setSign2Label(s != null && s.getSign2Label() != null ? s.getSign2Label() : "Received By");
        return r;
    }

    @SuppressWarnings("null")
    private ResponseEntity<byte[]> pdfResponse(PrintInvoiceData data, PrintRequest req) {
        byte[] pdf = pdfService.generatePdf(data, req);
        String filename = "invoice-" + data.getInvoiceNo() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @SuppressWarnings("null")
    private ResponseEntity<String> previewResponse(PrintInvoiceData data, PrintRequest req) {
        String html = pdfService.generateHtmlPreview(data, req);
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(html);
    }
}
