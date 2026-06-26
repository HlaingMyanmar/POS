package org.sspd.servicemgmt.printingoptions.service;

import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfig;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintLineItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure, stateless pagination service.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Split a flat list of {@link PrintLineItem}s into page buckets.</li>
 *   <li>Clone a base {@link PrintInvoiceData} for each page, filling in
 *       page-specific fields ({@code currentPage}, {@code pageItems},
 *       {@code isFirstPage}, {@code isLastPage}).</li>
 * </ul>
 *
 * <h3>What this class does NOT do</h3>
 * <ul>
 *   <li>No Thymeleaf rendering — see {@link TemplateRenderingService}.</li>
 *   <li>No PDF generation — see {@link HtmlPdfService}.</li>
 *   <li>No database access — see {@link InvoiceAssemblerService}.</li>
 * </ul>
 *
 * <h3>Frontend mirror</h3>
 * This algorithm is intentionally identical to {@code paginationEngine.ts}
 * so HTML preview (frontend) and PDF output (backend) always paginate the
 * same way.  If you change this logic, update the TypeScript file too.
 */
@Service
public class PaginationService {

    // ─────────────────────────────────────────────────────────────────────────
    //  Core pagination
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Splits {@code items} into page buckets according to {@code config}.
     *
     * <p>Rows are placed in whole-row units; a row is never split across pages.
     * Thermal (continuous roll) paper is treated as a single infinite page.
     *
     * @param items  all line items for the invoice (ordered)
     * @param config layout config for the target paper size
     * @return ordered, non-empty list of page buckets (guaranteed ≥ 1 page)
     */
    public List<List<PrintLineItem>> paginate(
            List<PrintLineItem> items,
            PrintLayoutConfig config) {

        if (config.isThermal()) {
            // Thermal receipts: one continuous page, no splitting
            return List.of(items != null ? items : Collections.emptyList());
        }

        List<List<PrintLineItem>> pages = new ArrayList<>();
        List<PrintLineItem> src = items != null ? items : Collections.emptyList();

        if (src.isEmpty()) {
            pages.add(Collections.emptyList());
            return pages;
        }

        int idx         = 0;
        boolean isFirst = true;

        while (idx < src.size()) {
            int limit = isFirst
                    ? config.getRowsOnFirstPage()
                    : config.getRowsOnContinuationPage();

            int end = Math.min(idx + limit, src.size());
            pages.add(new ArrayList<>(src.subList(idx, end)));
            idx     = end;
            isFirst = false;
        }

        return pages;
    }

    /**
     * Builds a list of page-specific {@link PrintInvoiceData} snapshots
     * ready for template rendering.
     *
     * @param base   fully assembled base invoice data (all items present)
     * @param config layout config for the target paper size
     * @return one snapshot per page (≥ 1 entry)
     */
    public List<PrintInvoiceData> buildPages(
            PrintInvoiceData base,
            PrintLayoutConfig config) {

        List<List<PrintLineItem>> buckets = paginate(base.getLineItems(), config);
        int total = buckets.size();
        List<PrintInvoiceData> pages = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            pages.add(pageSnapshot(base, buckets.get(i), i + 1, total));
        }

        return pages;
    }

    /**
     * Returns the total page count for a given item count and config.
     * Useful for pre-rendering page counters without building full snapshots.
     */
    public int countPages(int itemCount, PrintLayoutConfig config) {
        if (config.isThermal() || itemCount == 0) return 1;

        int remaining = itemCount;
        int pages     = 0;
        boolean first = true;

        while (remaining > 0) {
            int limit  = first ? config.getRowsOnFirstPage() : config.getRowsOnContinuationPage();
            remaining -= limit;
            pages++;
            first = false;
        }

        return pages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a shallow copy of {@code base} with page-specific fields filled in.
     * Only primitive / immutable fields are copied — collections are shared
     * (they are read-only at this point).
     */
    private PrintInvoiceData pageSnapshot(
            PrintInvoiceData base,
            List<PrintLineItem> pageItems,
            int currentPage,
            int totalPages) {

        return PrintInvoiceData.builder()
                // ── Company ──────────────────────────────────────────────────
                .companyName(base.getCompanyName())
                .companyAddress(base.getCompanyAddress())
                .companyPhone(base.getCompanyPhone())
                .companyEmail(base.getCompanyEmail())
                .logoBase64(base.getLogoBase64())
                .footerNote(base.getFooterNote())
                .headerColor(base.getHeaderColor())
                // ── Invoice meta ─────────────────────────────────────────────
                .documentType(base.getDocumentType())
                .invoiceTitle(base.getInvoiceTitle())
                .invoiceNo(base.getInvoiceNo())
                .invoiceDate(base.getInvoiceDate())
                .dueDate(base.getDueDate())
                .paymentStatus(base.getPaymentStatus())
                .creditStatus(base.getCreditStatus())
                // ── Parties ──────────────────────────────────────────────────
                .customerName(base.getCustomerName())
                .customerPhone(base.getCustomerPhone())
                .customerAddress(base.getCustomerAddress())
                .cashierName(base.getCashierName())
                // ── All items (for reference) + this page's subset ───────────
                .lineItems(base.getLineItems())
                .pageItems(pageItems)
                // ── Payment history ──────────────────────────────────────────
                .payments(base.getPayments())
                // ── Totals ───────────────────────────────────────────────────
                .subtotal(base.getSubtotal())
                .discount(base.getDiscount())
                .netAmount(base.getNetAmount())
                .paid(base.getPaid())
                .balanceDue(base.getBalanceDue())
                // ── Extra ─────────────────────────────────────────────────────
                .remark(base.getRemark())
                .customerNotice(base.getCustomerNotice())
                // ── Service Job / Intake Sheet ────────────────────────────────
                .itemName(base.getItemName())
                .problemDesc(base.getProblemDesc())
                .accessories(base.getAccessories())
                .estimatedCost(base.getEstimatedCost())
                .deviceConditionRows(base.getDeviceConditionRows())
                // ── Booking / Device Intake Receipt ───────────────────────────
                .bookingReceipt(base.isBookingReceipt())
                .deviceType(base.getDeviceType())
                .brand(base.getBrand())
                .model(base.getModel())
                .serialNo(base.getSerialNo())
                .color(base.getColor())
                .deviceRows(base.getDeviceRows())
                // ── Print options ─────────────────────────────────────────────
                .pageConfig(base.getPageConfig())
                .showLogo(base.isShowLogo())
                .showSerial(base.isShowSerial())
                .showPaymentHistory(base.isShowPaymentHistory())
                .showSignatures(base.isShowSignatures())
                .showQrCode(base.isShowQrCode())
                .qrCodeBase64(base.getQrCodeBase64())
                .sign1Label(base.getSign1Label())
                .sign2Label(base.getSign2Label())
                // ── Typography ────────────────────────────────────────────────
                .headerFontFamily(base.getHeaderFontFamily())
                .headerFontSizePx(base.getHeaderFontSizePx())
                .infoFontFamily(base.getInfoFontFamily())
                .infoFontSizePx(base.getInfoFontSizePx())
                .tableHeaderFontFamily(base.getTableHeaderFontFamily())
                .tableHeaderFontSizePx(base.getTableHeaderFontSizePx())
                .tableDataFontFamily(base.getTableDataFontFamily())
                .tableDataFontSizePx(base.getTableDataFontSizePx())
                .footerFontFamily(base.getFooterFontFamily())
                .footerFontSizePx(base.getFooterFontSizePx())
                .noticeFontFamily(base.getNoticeFontFamily())
                .noticeFontSizePx(base.getNoticeFontSizePx())
                // ── Page-specific fields ──────────────────────────────────────
                .currentPage(currentPage)
                .totalPages(totalPages)
                .firstPage(currentPage == 1)
                .lastPage(currentPage == totalPages)
                .build();
    }
}
