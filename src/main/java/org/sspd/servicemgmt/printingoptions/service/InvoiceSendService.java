package org.sspd.servicemgmt.printingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerMailService;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

@Service
@RequiredArgsConstructor
public class InvoiceSendService {

    private final InvoiceAssemblerService assembler;
    private final HtmlPdfService pdfService;
    private final VoucherSettingService voucherSettings;
    private final CustomerMailService mail;
    private final SaleRepository sales;
    private final BookingRepository bookings;
    private final ServiceJobRepository jobs;

    @Transactional(readOnly = true)
    public String send(PrintRequest req) {
        VoucherSetting s = voucherSettings.findEntity(req.getDocumentType()).orElse(null);
        PrintInvoiceData data = assembler.assemble(req, s);
        byte[] pdf = pdfService.generatePdf(data, req);
        String to = resolveTo(req);
        String no = data.getInvoiceNo() == null ? String.valueOf(req.getDocumentId()) : data.getInvoiceNo();
        String filename = "invoice-" + no.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        String company = data.getCompanyName() == null ? "SSPD" : data.getCompanyName();
        mail.sendPdf(
                to,
                company + " — Invoice " + no,
                company + "\n\nSale Invoice (" + no + ") ကို ဖိုင်တွဲပို့လိုက်ပါသည်။\n",
                filename,
                pdf
        );
        return to;
    }

    private String resolveTo(PrintRequest req) {
        if (StringUtils.hasText(req.getToEmail())) {
            return req.getToEmail().trim();
        }
        String found = lookupStoredEmail(req);
        if (!StringUtils.hasText(found) || !found.contains("@")) {
            throw new IllegalArgumentException("ဖောက်သည် email မရှိပါ — ပို့မည့် email ထည့်ပါ");
        }
        return found.trim();
    }

    private String lookupStoredEmail(PrintRequest req) {
        Integer id = req.getDocumentId();
        if (id == null || req.getDocumentType() == null) return null;
        return switch (req.getDocumentType()) {
            case SALE -> sales.findById(id)
                    .map(s -> s.getCustomer() == null ? null : s.getCustomer().getEmail())
                    .orElse(null);
            case BOOKING -> bookings.findById(id)
                    .map(b -> b.getCustomer() == null ? null : b.getCustomer().getEmail())
                    .orElse(null);
            case SERVICE_JOB, SERVICE_DONE -> jobs.findById(id)
                    .map(j -> j.getCustomer() == null ? null : j.getCustomer().getEmail())
                    .orElse(null);
            case PURCHASE -> null;
        };
    }
}
