package org.sspd.servicemgmt.printingoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xml.sax.InputSource;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfig;
import org.sspd.servicemgmt.printingoptions.config.PrintLayoutConfigRegistry;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PDF generation orchestrator — the <em>only</em> class that knows about
 * Flying Saucer / ITextRenderer.
 *
 * <h3>Responsibility</h3>
 * <ol>
 *   <li>Resolve the layout config from {@link PrintLayoutConfigRegistry}.</li>
 *   <li>Ask {@link PaginationService} for page snapshots.</li>
 *   <li>Ask {@link TemplateRenderingService} for rendered XHTML.</li>
 *   <li>Wrap the XHTML in a valid document root.</li>
 *   <li>Pass the document to Flying Saucer and return PDF bytes.</li>
 * </ol>
 *
 * <h3>What this class does NOT do</h3>
 * <ul>
 *   <li>No Thymeleaf — that is {@link TemplateRenderingService}.</li>
 *   <li>No pagination math — that is {@link PaginationService}.</li>
 *   <li>No database access — that is {@link InvoiceAssemblerService}.</li>
 *   <li>No layout constants — those are in {@link PrintLayoutConfigRegistry}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlPdfService {

    private final PrintLayoutConfigRegistry configRegistry;
    private final PaginationService         paginationService;
    private final TemplateRenderingService  templateService;
    private final DynamicPrintConfigService dynamicConfig;

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a complete multi-page PDF from a fully assembled invoice.
     *
     * @param data      base invoice data (all line items present)
     * @param paperSize paper size key, e.g. "A4", "A5", "POS_80MM"
     * @return raw PDF bytes, ready to stream as {@code application/pdf}
     */
    public byte[] generatePdf(PrintInvoiceData data, String paperSize) {
        PrintLayoutConfig config = configRegistry.get(paperSize);
        log.info("Generating PDF: invoice={}, paper={}, rowsFirst={}, rowsCont={}",
                data.getInvoiceNo(), config.getName(),
                config.getRowsOnFirstPage(), config.getRowsOnContinuationPage());

        List<PrintInvoiceData> pages = paginationService.buildPages(data, config);
        String body = templateService.renderPdfBody(pages, config);
        String xhtml = wrapForPdf(body, config, pages.isEmpty() ? null : pages.get(0));
        return xhtmlToPdf(xhtml, data.getInvoiceNo());
    }

    /**
     * Renders an HTML preview suitable for injecting into an iframe.
     *
     * @param data      base invoice data
     * @param paperSize paper size key
     * @return full HTML document string
     */
    public String generateHtmlPreview(PrintInvoiceData data, String paperSize) {
        PrintLayoutConfig config = configRegistry.get(paperSize);
        List<PrintInvoiceData> pages = paginationService.buildPages(data, config);
        return templateService.renderHtmlPreview(pages, config);
    }

    /**
     * Convenience overload: resolves layout from the DB setting for the given
     * {@link PrintRequest#getDocumentType()}, falling back to the registry.
     */
    public byte[] generatePdf(PrintInvoiceData data, PrintRequest req) {
        PrintLayoutConfig config = dynamicConfig.resolveConfig(req.getDocumentType(), req.getPaperSize());
        log.info("Generating PDF: invoice={}, paper={}, rowsFirst={}, rowsCont={}",
                data.getInvoiceNo(), config.getName(),
                config.getRowsOnFirstPage(), config.getRowsOnContinuationPage());
        List<PrintInvoiceData> pages = paginationService.buildPages(data, config);
        String body = templateService.renderPdfBody(pages, config);
        String xhtml = wrapForPdf(body, config, pages.isEmpty() ? null : pages.get(0));
        return xhtmlToPdf(xhtml, data.getInvoiceNo());
    }

    public String generateHtmlPreview(PrintInvoiceData data, PrintRequest req) {
        PrintLayoutConfig config = dynamicConfig.resolveConfig(req.getDocumentType(), req.getPaperSize());
        List<PrintInvoiceData> pages = paginationService.buildPages(data, config);
        return templateService.renderHtmlPreview(pages, config);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  XHTML document wrapping
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wraps page body fragments in a complete, valid XHTML 1.0 Strict document
     * that Flying Saucer can parse and render.
     *
     * <p>The {@code @page} rule here controls the PDF page size and margins.
     * It must match the physical dimensions in {@link PrintLayoutConfig}.
     *
     * <p>To change PDF margins, edit the {@code margin} values here — they
     * are read directly from {@link PrintLayoutConfig} and kept in sync
     * with the Thymeleaf template margins.
     */
    String wrapForPdf(String body, PrintLayoutConfig cfg, PrintInvoiceData firstPage) {
        String fontCss = templateService.buildFontOverrideCss(firstPage);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                  <style type="text/css">
                    @page {
                      size: %s;
                      margin: %.1fmm %.1fmm %.1fmm %.1fmm;
                    }
                    *, *::before, *::after { box-sizing: border-box; }
                    body { margin: 0; padding: 0; }
                    .invoice-page { page-break-after: always; }
                    .invoice-page:last-child { page-break-after: avoid; }
                    %s
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(
                cfg.getCssPageSize(),
                cfg.getMarginTopMm(), cfg.getMarginRightMm(),
                cfg.getMarginBottomMm(), cfg.getMarginLeftMm(),
                fontCss,
                body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Flying Saucer PDF conversion
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] xhtmlToPdf(String xhtml, String invoiceNo) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = parseXhtml(xhtml);

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocument(doc, null);
            renderer.layout();
            renderer.createPDF(out);

            byte[] pdf = out.toByteArray();
            log.info("PDF generated: invoice={}, size={}KB", invoiceNo, pdf.length / 1024);
            return pdf;

        } catch (Exception e) {
            throw new PdfGenerationException(
                    "PDF generation failed for invoice: " + invoiceNo, e);
        }
    }

    private Document parseXhtml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        // Prevent external DTD fetch — Flying Saucer handles XHTML doctype internally
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        // Suppress DTD resolution errors for the XHTML 1.0 Strict doctype
        builder.setEntityResolver((publicId, systemId) ->
                new InputSource(new ByteArrayInputStream(new byte[0])));

        return builder.parse(
                new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Exception type
    // ─────────────────────────────────────────────────────────────────────────

    public static final class PdfGenerationException extends RuntimeException {
        public PdfGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
