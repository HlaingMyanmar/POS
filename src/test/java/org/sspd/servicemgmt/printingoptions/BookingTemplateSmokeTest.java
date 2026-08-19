package org.sspd.servicemgmt.printingoptions;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

class BookingTemplateSmokeTest {
    @Test
    void rendersBookingTemplate() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        var device = PrintInvoiceData.DeviceRow.builder()
            .deviceType("Phone").brand("Test").model("Model")
            .serialNo("SN-1").color("Black").accessories("Charger")
            .problemDesc("No power").deviceConditions("Scratch on corner").build();
        var invoice = PrintInvoiceData.builder()
            .invoiceNo("INT-001").invoiceDate("2026-08-18")
            .customerName("Customer").paymentStatus("Pending")
            .shelfLocation("A-01").estimatedCost("10000")
            .deviceRows(List.of(device)).currentPage(1).totalPages(1).build();
        var context = new Context();
        context.setVariable("inv", invoice);
        context.setVariable("layout", null);
        String body = engine.process("print/booking-a5", context);
        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html><head><title>Test</title></head><body>" + body + "</body></html>";
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            var document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xhtml.getBytes(StandardCharsets.UTF_8)));
            var renderer = new ITextRenderer();
            renderer.setDocument(document, null);
            renderer.layout();
            renderer.createPDF(new ByteArrayOutputStream());
        } catch (Exception error) {
            throw new AssertionError("Booking template must produce valid PDF XHTML:\n" + xhtml.substring(0, Math.min(1200, xhtml.length())), error);
        }
    }
}
