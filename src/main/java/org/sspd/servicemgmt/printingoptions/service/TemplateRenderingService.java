package org.sspd.servicemgmt.printingoptions.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfig;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Converts {@link PrintInvoiceData} page snapshots into XHTML strings
 * using Thymeleaf.
 *
 * <h3>Layer contract</h3>
 * <pre>
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  InvoiceAssemblerService  →  builds PrintInvoiceData from DB     │
 *   │  PaginationService        →  splits items into page snapshots    │
 *   │  TemplateRenderingService →  renders each snapshot as XHTML      │  ← here
 *   │  HtmlPdfService           →  assembles XHTML → PDF               │
 *   └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Maintainability</h3>
 * <ul>
 *   <li>To change which template is used for A5 — edit {@link #resolveTemplate}.</li>
 *   <li>To redesign the invoice header — edit
 *       {@code templates/print/fragments/invoice-header.html}.
 *       No Java code changes needed.</li>
 *   <li>To change the HTML preview wrapper (browser chrome) — edit
 *       {@link #buildPreviewShell}.  The PDF wrapper lives in
 *       {@link HtmlPdfService}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRenderingService {

    private final TemplateEngine templateEngine;

    private String printCss = "";

    @PostConstruct
    void loadPrintCss() {
        try {
            var res = new ClassPathResource("print/css/print-base.css");
            printCss = res.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("print-base.css not found on classpath — styles will be missing in preview");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Single-page rendering
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders one invoice page into an XHTML string.
     *
     * <p>The result is a complete, self-contained XHTML {@code <div>} fragment
     * (not a full document).  {@link HtmlPdfService} wraps multiple fragments
     * inside a document root for Flying Saucer.
     *
     * @param pageData snapshot for this page (pageItems, currentPage, etc.)
     * @param config   layout config — passed to the template as {@code layout}
     * @return XHTML fragment string
     */
    public String renderPageFragment(PrintInvoiceData pageData, PrintLayoutConfig config) {
        Context ctx = buildContext(pageData, config);
        String template = resolveTemplate(config, pageData);
        log.debug("Rendering template '{}' for page {}/{} — {} items",
                template, pageData.getCurrentPage(), pageData.getTotalPages(),
                pageData.getPageItems() != null ? pageData.getPageItems().size() : 0);
        return templateEngine.process(template, ctx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-page rendering for browser HTML preview
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders all pages and concatenates them inside an HTML shell suitable
     * for injection into an iframe ({@code srcDoc}).
     *
     * <p>The shell includes the {@code @page} CSS rule, body background, and
     * print reset — everything needed for an in-browser print preview.
     *
     * @param pages  ordered list of page snapshots from {@link PaginationService}
     * @param config layout config for the target paper size
     * @return complete HTML document string
     */
    public String renderHtmlPreview(List<PrintInvoiceData> pages, PrintLayoutConfig config) {
        PrintInvoiceData first = pages.isEmpty() ? null : pages.get(0);
        StringBuilder sb = new StringBuilder(buildPreviewShell(config, first));
        for (PrintInvoiceData page : pages) {
            sb.append(renderPageFragment(page, config));
        }
        sb.append("\n</body>\n</html>");
        return sb.toString();
    }

    /** Returns the font-override CSS block for embedding in PDF wrapper. */
    public String buildFontOverrideCss(PrintInvoiceData data) {
        if (data == null) return "";
        StringBuilder css = new StringBuilder();
        appendFontRule(css,
            ".inv-header,.inv-cont-header,.inv-header__company-name,.inv-header__company-sub," +
            ".inv-header__meta-label,.inv-header__meta-number,.inv-header__meta-date," +
            ".inv-cont-header__title,.inv-cont-header__page",
            data.getHeaderFontFamily(), data.getHeaderFontSizePx());
        appendFontRule(css,
            ".inv-block__title,.inv-block__label,.inv-block__value,.inv-badge",
            data.getInfoFontFamily(), data.getInfoFontSizePx());
        appendFontRule(css,
            ".inv-table th,.inv-pay-table th",
            data.getTableHeaderFontFamily(), data.getTableHeaderFontSizePx());
        appendFontRule(css,
            ".inv-table td,.inv-pay-table td,.inv-summary-row",
            data.getTableDataFontFamily(), data.getTableDataFontSizePx());
        appendFontRule(css,
            ".inv-footer,.inv-sign__line,.inv-section-label",
            data.getFooterFontFamily(), data.getFooterFontSizePx());
        appendFontRule(css,
            ".inv-notice,.inv-remark",
            data.getNoticeFontFamily(), data.getNoticeFontSizePx());
        return css.toString();
    }

    private void appendFontRule(StringBuilder css, String selector, String family, Integer sizePx) {
        if ((family == null || family.isBlank()) && sizePx == null) return;
        css.append(selector).append("{");
        if (family != null && !family.isBlank())
            css.append("font-family:").append(family).append(",sans-serif !important;");
        if (sizePx != null)
            css.append("font-size:").append(sizePx).append("px !important;");
        css.append("}");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-page rendering for Flying Saucer PDF
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders all pages as concatenated XHTML fragments for the PDF engine.
     * <em>Does not</em> include the outer XHTML document wrapper — that is
     * added by {@link HtmlPdfService#wrapForPdf}.
     *
     * @param pages  ordered list of page snapshots
     * @param config layout config
     * @return raw XHTML body content (multiple {@code .invoice-page} divs)
     */
    public String renderPdfBody(List<PrintInvoiceData> pages, PrintLayoutConfig config) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            sb.append(renderPageFragment(pages.get(i), config));
            if (i < pages.size() - 1) {
                // Visual separator between pages — Flying Saucer uses page-break-after: always on .invoice-page
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps paper size + document type to a Thymeleaf template name.
     *
     * <p>Template resolution:
     * <pre>
     *   BOOKING      + A4/A5  → print/booking-a4  / print/booking-a5
     *   SERVICE_JOB  + A4/A5  → print/service-job-a4 / print/service-job-a5
     *   SERVICE_DONE + A4/A5  → print/service-done-a4 / print/service-done-a5
     *   SALE/PURCHASE+ A4/A5  → print/invoice-a4 / print/invoice-a5
     *   POS_80MM/58MM (any)   → print/invoice-pos
     * </pre>
     *
     * To add a new document type or paper size, edit only this method.
     */
    private String resolveTemplate(PrintLayoutConfig config, PrintInvoiceData data) {
        String dt = data.getDocumentType() != null ? data.getDocumentType() : "";
        return switch (config.getName()) {
            case "POS_80MM", "POS_58MM" -> "print/invoice-pos";
            case "A5" -> switch (dt) {
                case "BOOKING"      -> "print/booking-a5";
                case "SERVICE_JOB"  -> "print/service-job-a5";
                case "SERVICE_DONE" -> "print/service-done-a5";
                default             -> "print/invoice-a5";
            };
            default -> switch (dt) { // A4 is the default paper size
                case "BOOKING"      -> "print/booking-a4";
                case "SERVICE_JOB"  -> "print/service-job-a4";
                case "SERVICE_DONE" -> "print/service-done-a4";
                default             -> "print/invoice-a4";
            };
        };
    }

    /** Builds the Thymeleaf context with all variables the templates expect. */
    private Context buildContext(PrintInvoiceData pageData, PrintLayoutConfig config) {
        Context ctx = new Context();
        ctx.setVariable("inv",    pageData);   // invoice data
        ctx.setVariable("layout", config);     // layout constants (rowHeightPx, etc.)
        ctx.setVariable("cfg",    config);     // alias — templates may use either name
        return ctx;
    }

    /**
     * HTML shell for browser-based preview.
     *
     * <p>Includes:
     * <ul>
     *   <li>{@code @page} size rule — so Ctrl+P from inside the iframe uses
     *       the correct paper size.</li>
     *   <li>Light grey body background — makes page boundaries visible.</li>
     *   <li>Print media reset — removes background when actually printing.</li>
     * </ul>
     *
     * To change the preview chrome (background colour, page shadow) edit here.
     * The PDF wrapper is separate — see {@link HtmlPdfService#wrapForPdf}.
     */
    private String buildPreviewShell(PrintLayoutConfig config, PrintInvoiceData data) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <style>
                    %s
                    @page { size: %s; margin: %.1fmm %.1fmm %.1fmm %.1fmm; }
                    body { padding: 16px; background: #e5e7eb; }
                    .invoice-page { box-shadow: 0 2px 16px rgba(30,58,95,.12); margin-bottom: 20px; }
                    %s
                  </style>
                </head>
                <body>
                """.formatted(printCss,
                              config.getCssPageSize(),
                              config.getMarginTopMm(), config.getMarginRightMm(),
                              config.getMarginBottomMm(), config.getMarginLeftMm(),
                              buildFontOverrideCss(data));
    }
}
