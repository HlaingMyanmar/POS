package org.sspd.servicemgmt.purchaseoptions.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseOcrPreviewDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pragmatic invoice OCR/import preview:
 * - text/plain, csv, or text-based files are parsed with regex
 * - images/PDFs without embedded text return an empty scaffold + guidance note
 * (full image OCR engines can be plugged in later without API change)
 */
@Service
public class PurchaseOcrService {

    private static final Pattern INVOICE_NO = Pattern.compile("(?i)(?:invoice|inv|bill)\\s*[#:.-]?\\s*([A-Z0-9-]{4,})");
    private static final Pattern MONEY = Pattern.compile("(?i)(?:total|amount|grand\\s*total)\\s*[:\\-]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern TAX = Pattern.compile("(?i)(?:tax|vat|gst)\\s*[:\\-]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern LINE = Pattern.compile("(?m)^\\s*(.+?)\\s+[xX*]\\s*(\\d+)\\s+[\\@=]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*$");

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_IMPORT','CAN_ACCESS_PURCHASE_CREATE')")
    public PurchaseOcrPreviewDTO preview(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean textLike = contentType.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".tsv");

        String raw;
        String note;
        if (textLike) {
            raw = new String(file.getBytes(), StandardCharsets.UTF_8);
            note = "Parsed from text invoice. Review before confirming purchase.";
        } else {
            raw = "";
            note = "Image/PDF OCR engine is not configured on this server. Upload a .txt/.csv invoice export, or fill the form manually. Attachment can still be saved on the voucher.";
        }

        String invoiceNo = first(INVOICE_NO, raw);
        BigDecimal total = money(MONEY, raw);
        BigDecimal tax = money(TAX, raw);
        List<PurchaseOcrPreviewDTO.Line> lines = new ArrayList<>();
        Matcher lm = LINE.matcher(raw);
        while (lm.find()) {
            lines.add(PurchaseOcrPreviewDTO.Line.builder()
                    .productHint(lm.group(1).trim())
                    .qty(Integer.parseInt(lm.group(2)))
                    .unitCost(new BigDecimal(lm.group(3)))
                    .build());
        }
        return PurchaseOcrPreviewDTO.builder()
                .supplierInvoiceNo(invoiceNo)
                .suggestedTotal(total)
                .suggestedTax(tax)
                .rawText(raw.length() > 4000 ? raw.substring(0, 4000) : raw)
                .note(note)
                .lines(lines)
                .build();
    }

    private String first(Pattern p, String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private BigDecimal money(Pattern p, String text) {
        String v = first(p, text);
        if (v == null) return null;
        try { return new BigDecimal(v); } catch (Exception e) { return null; }
    }
}
