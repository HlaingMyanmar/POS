package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingDeviceInfo;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.reportoptions.dto.*;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class JasperVoucherService {

    private final SaleRepository saleRepository;
    private final BookingRepository bookingRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CompanySettingsService companySettingsService;

    private final Map<String, JasperReport> reportCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ─── Public API ─────────────────────────────────────────────────────────────

    public byte[] generateSaleStandard(Integer saleId) throws JRException {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NoSuchElementException("Sale not found: " + saleId));

        CompanySettingsDTO cs = companySettingsService.getSettings();

        List<SaleLineRow> items = buildSaleLines(sale);
        List<PaymentRow>  payments = buildPaymentRows(saleId, ReferenceType.Sale);

        JasperReport mainReport    = loadReport("sale_standard");
        JasperReport itemsReport   = loadReport("service_parts_subreport");
        JasperReport paymentReport = loadReport("payment_subreport");

        Map<String, Object> params = new HashMap<>();
        fillCompanyParams(params, cs);
        params.put("INVOICE_TITLE",   cs.getInvoiceTitle() != null ? cs.getInvoiceTitle() : "SALES INVOICE");
        params.put("INVOICE_NO",      sale.getSaleCode());
        params.put("INVOICE_DATE",    sale.getSaleDate() != null ? sale.getSaleDate().format(DT_FMT) : "");
        params.put("DUE_DATE",        sale.getDueDate() != null ? sale.getDueDate().format(D_FMT) : "");
        params.put("PAYMENT_STATUS",  sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : "");
        params.put("CUSTOMER_NAME",   sale.getCustomer() != null ? sale.getCustomer().getName() : "");
        params.put("CUSTOMER_PHONE",  sale.getCustomer() != null ? sale.getCustomer().getPhone() : "");
        params.put("CUSTOMER_ADDR",   sale.getCustomer() != null ? nullSafe(sale.getCustomer().getAddress()) : "");
        params.put("CASHIER_NAME",    sale.getStaff() != null ? sale.getStaff().getName() : "");
        params.put("REMARK",          nullSafe(sale.getRemark()));
        params.put("SUBTOTAL_STR",    formatMoney(sale.getTotalAmount()));
        params.put("DISCOUNT_STR",    formatMoney(sale.getDiscountAmount()));
        params.put("NET_TOTAL_STR",   formatMoney(sale.getNetAmount()));
        params.put("PAID_STR",        formatMoney(sale.getPaidAmount()));
        params.put("DUE_STR",         formatMoney(sale.getDueAmount()));
        params.put("ITEMS_SOURCE",    new JRBeanCollectionDataSource(items));
        params.put("PAYMENT_SOURCE",  new JRBeanCollectionDataSource(payments));
        params.put("ITEMS_SUBREPORT", itemsReport);
        params.put("PAYMENT_SUBREPORT", paymentReport);

        return exportToPdf(mainReport, params);
    }

    public byte[] generateSalePos(Integer saleId) throws JRException {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NoSuchElementException("Sale not found: " + saleId));

        CompanySettingsDTO cs = companySettingsService.getSettings();
        List<SaleLineRow> items = buildSaleLines(sale);

        JasperReport mainReport  = loadReport("sale_pos");

        Map<String, Object> params = new HashMap<>();
        params.put("COMPANY_NAME",    cs.getCompanyName());
        params.put("COMPANY_PHONE",   nullSafe(cs.getCompanyPhone()));
        params.put("INVOICE_TITLE",   cs.getInvoiceTitle() != null ? cs.getInvoiceTitle() : "SALES RECEIPT");
        params.put("FOOTER_NOTE",     nullSafe(cs.getFooterNote()));
        params.put("INVOICE_NO",      sale.getSaleCode());
        params.put("INVOICE_DATE",    sale.getSaleDate() != null ? sale.getSaleDate().format(DT_FMT) : "");
        params.put("CUSTOMER_NAME",   sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in");
        params.put("CASHIER_NAME",    sale.getStaff() != null ? sale.getStaff().getName() : "");
        params.put("PAYMENT_STATUS",  sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : "");
        params.put("SUBTOTAL_STR",    formatMoney(sale.getTotalAmount()));
        params.put("DISCOUNT_STR",    formatMoney(sale.getDiscountAmount()));
        params.put("NET_TOTAL_STR",   formatMoney(sale.getNetAmount()));
        params.put("PAID_STR",        formatMoney(sale.getPaidAmount()));
        params.put("DUE_STR",         formatMoney(sale.getDueAmount()));
        params.put("ITEMS_SUBREPORT", loadReport("service_parts_subreport"));

        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(items);
        JasperPrint print = JasperFillManager.fillReport(mainReport, params, ds);
        return JasperExportManager.exportReportToPdf(print);
    }

    public byte[] generateBookingReceipt(Integer bookingId) throws JRException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        CompanySettingsDTO cs = companySettingsService.getSettings();

        List<DeviceInfoRow> deviceInfoRows = new ArrayList<>();
        if (booking.getDeviceInfos() != null) {
            int i = 0;
            for (BookingDeviceInfo di : booking.getDeviceInfos()) {
                deviceInfoRows.add(new DeviceInfoRow(
                        nullSafe(di.getName()),
                        nullSafe(di.getDescription()),
                        nullSafe(di.getStatus()),
                        nullSafe(di.getNotice())
                ));
                i++;
            }
        }

        JasperReport mainReport   = loadReport("booking_receipt");
        JasperReport deviceReport = loadReport("device_info_subreport");

        Map<String, Object> params = new HashMap<>();
        params.put("COMPANY_NAME",    cs.getCompanyName());
        params.put("COMPANY_ADDRESS", nullSafe(cs.getCompanyAddress()));
        params.put("COMPANY_PHONE",   nullSafe(cs.getCompanyPhone()));
        params.put("INVOICE_TITLE",   "DEVICE INTAKE RECEIPT");
        params.put("FOOTER_NOTE",     nullSafe(cs.getFooterNote()));
        params.put("INVOICE_NO",      booking.getInvoiceNo());
        params.put("BOOKING_DATE",    booking.getBookingDate() != null ? booking.getBookingDate().format(DT_FMT) : "");
        params.put("APPOINTMENT_DATE", booking.getAppointmentDate() != null ? booking.getAppointmentDate().format(DT_FMT) : "");
        params.put("STATUS",          booking.getStatus() != null ? booking.getStatus().name() : "");
        params.put("CUSTOMER_NAME",   booking.getCustomer() != null ? booking.getCustomer().getName() : "");
        params.put("CUSTOMER_PHONE",  booking.getCustomer() != null ? nullSafe(booking.getCustomer().getPhone()) : "");
        params.put("RECEPTIONIST",    booking.getStaff() != null ? booking.getStaff().getName() : "");
        params.put("DEVICE_TYPE",     nullSafe(booking.getDeviceType()));
        params.put("BRAND",           nullSafe(booking.getBrand()));
        params.put("MODEL",           nullSafe(booking.getModel()));
        params.put("SERIAL_NO",       nullSafe(booking.getSerialNumber()));
        params.put("COLOR",           nullSafe(booking.getColor()));
        params.put("ACCESSORIES",     nullSafe(booking.getAccessories()));
        params.put("TOTAL_AMOUNT",    formatMoney(booking.getTotalAmount()));
        params.put("REMARK",          nullSafe(booking.getRemark()));
        params.put("DEVICE_INFO_SOURCE",   new JRBeanCollectionDataSource(deviceInfoRows));
        params.put("DEVICE_INFO_SUBREPORT", deviceReport);

        return exportToPdf(mainReport, params);
    }

    public byte[] generateServiceVoucher(Integer jobId) throws JRException {
        ServiceJob job = serviceJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("ServiceJob not found: " + jobId));

        CompanySettingsDTO cs = companySettingsService.getSettings();

        List<ServiceLineRow> serviceLines = new ArrayList<>();
        if (job.getLines() != null) {
            int i = 1;
            for (ServiceJobLine l : job.getLines()) {
                serviceLines.add(new ServiceLineRow(
                        i++,
                        l.getServiceItem() != null ? l.getServiceItem().getItem() : "",
                        l.getQty(),
                        formatMoney(l.getPrice()),
                        formatMoney(l.getSubtotal())
                ));
            }
        }

        List<ServicePartRow> serviceParts = new ArrayList<>();
        if (job.getProductParts() != null) {
            int i = 1;
            for (ServiceJobPart p : job.getProductParts()) {
                String serialInfo = p.getSerialNumbers() != null ? p.getSerialNumbers() : "";
                serviceParts.add(new ServicePartRow(
                        i++,
                        p.getProduct() != null ? p.getProduct().getName() : "",
                        serialInfo,
                        p.getQty(),
                        formatMoney(p.getUnitPrice()),
                        formatMoney(p.getSubtotal())
                ));
            }
        }

        BigDecimal laborTotal = job.getLines() == null ? BigDecimal.ZERO :
                job.getLines().stream().map(ServiceJobLine::getSubtotal)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal partsTotal = job.getProductParts() == null ? BigDecimal.ZERO :
                job.getProductParts().stream().map(ServiceJobPart::getSubtotal)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        JasperReport mainReport     = loadReport("service_voucher");
        JasperReport linesReport    = loadReport("service_lines_subreport");
        JasperReport partsReport    = loadReport("service_parts_subreport");

        Map<String, Object> params = new HashMap<>();
        params.put("COMPANY_NAME",    cs.getCompanyName());
        params.put("COMPANY_ADDRESS", nullSafe(cs.getCompanyAddress()));
        params.put("COMPANY_PHONE",   nullSafe(cs.getCompanyPhone()));
        params.put("FOOTER_NOTE",     nullSafe(cs.getFooterNote()));
        params.put("JOB_NO",          job.getJobNo());
        params.put("RECEIVED_DATE",   job.getReceivedDate() != null ? job.getReceivedDate().format(DT_FMT) : "");
        params.put("COMPLETED_DATE",  job.getCompletedDate() != null ? job.getCompletedDate().format(DT_FMT) : "");
        params.put("STATUS",          job.getStatus() != null ? job.getStatus().name() : "");
        params.put("PAYMENT_STATUS",  job.getPaymentStatus() != null ? job.getPaymentStatus().name() : "");
        params.put("CUSTOMER_NAME",   job.getCustomer() != null ? job.getCustomer().getName() : "");
        params.put("CUSTOMER_PHONE",  job.getCustomer() != null ? nullSafe(job.getCustomer().getPhone()) : "");
        params.put("ITEM_NAME",       nullSafe(job.getItemName()));
        params.put("ITEM_CONDITION",  nullSafe(job.getItemCondition()));
        params.put("PROBLEM_DESC",    nullSafe(job.getProblemDesc()));
        params.put("DIAGNOSIS_NOTES", nullSafe(job.getDiagnosisNotes()));
        params.put("TECHNICIAN",      job.getAssignedStaff() != null ? job.getAssignedStaff().getName() : "");
        params.put("ESTIMATED_COST",  job.getEstimatedCost() != null ? formatMoney(job.getEstimatedCost()) : "");
        params.put("DEVICE_CONDITIONS", formatConditionsText(job.getDeviceConditions()));
        // Accessories: entity's own value first, then booking fallback
        String accessories = nullSafe(job.getAccessories());
        String bookingNo = "";
        String color = "";
        String serialNo = "";
        if (job.getBookingId() != null) {
            Optional<org.sspd.servicemgmt.bookingoptions.model.Booking> bookingOpt =
                    bookingRepository.findById(job.getBookingId());
            if (bookingOpt.isPresent()) {
                var b = bookingOpt.get();
                bookingNo = b.getInvoiceNo() != null ? b.getInvoiceNo() : "";
                color     = nullSafe(b.getColor());
                serialNo  = nullSafe(b.getSerialNumber());
                if (accessories.isBlank()) accessories = nullSafe(b.getAccessories());
            }
        }
        params.put("BOOKING_NO",  bookingNo);
        params.put("COLOR",       color);
        params.put("SERIAL_NO",   serialNo);
        params.put("ACCESSORIES", accessories);
        params.put("LABOR_TOTAL",     formatMoney(laborTotal));
        params.put("PARTS_TOTAL",     formatMoney(partsTotal));
        params.put("DISCOUNT_STR",    formatMoney(job.getDiscountAmount()));
        params.put("NET_TOTAL_STR",   formatMoney(job.getNetAmount()));
        params.put("PAID_STR",        formatMoney(job.getPaidAmount()));
        params.put("DUE_STR",         formatMoney(job.getDueAmount()));
        params.put("REMARK",          nullSafe(job.getRemark()));
        params.put("SERVICE_LINES_SOURCE",    new JRBeanCollectionDataSource(serviceLines));
        params.put("SERVICE_PARTS_SOURCE",    new JRBeanCollectionDataSource(serviceParts));
        params.put("SERVICE_LINES_SUBREPORT", linesReport);
        params.put("SERVICE_PARTS_SUBREPORT", partsReport);

        return exportToPdf(mainReport, params);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private JasperReport loadReport(String name) throws JRException {
        return reportCache.computeIfAbsent(name, n -> {
            try {
                String path = "/reports/" + n + ".jrxml";
                InputStream is = getClass().getResourceAsStream(path);
                if (is == null) throw new IllegalStateException("Report not found: " + path);
                return JasperCompileManager.compileReport(is);
            } catch (JRException e) {
                throw new RuntimeException("Failed to compile report: " + name, e);
            }
        });
    }

    private byte[] exportToPdf(JasperReport report, Map<String, Object> params) throws JRException {
        JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
        return JasperExportManager.exportReportToPdf(print);
    }

    private void fillCompanyParams(Map<String, Object> params, CompanySettingsDTO cs) {
        params.put("COMPANY_NAME",    cs.getCompanyName());
        params.put("COMPANY_ADDRESS", nullSafe(cs.getCompanyAddress()));
        params.put("COMPANY_PHONE",   nullSafe(cs.getCompanyPhone()));
        params.put("COMPANY_EMAIL",   nullSafe(cs.getCompanyEmail()));
        params.put("FOOTER_NOTE",     nullSafe(cs.getFooterNote()));
        params.put("LOGO_BASE64",     nullSafe(cs.getLogoBase64()));
    }

    private List<SaleLineRow> buildSaleLines(Sale sale) {
        List<SaleLineRow> rows = new ArrayList<>();
        if (sale.getDetails() == null) return rows;
        int i = 1;
        for (SaleDetail d : sale.getDetails()) {
            String productName = d.getProduct() != null ? d.getProduct().getName() : "";
            String serial = d.getSerialNumber() != null ? d.getSerialNumber() : "";
            rows.add(new SaleLineRow(i++, productName, serial, d.getQty(),
                    formatMoney(d.getUnitPrice()), formatMoney(d.getSubtotal())));
        }
        return rows;
    }

    private List<PaymentRow> buildPaymentRows(Integer referenceId, ReferenceType type) {
        List<PaymentTransaction> txns = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(referenceId, type);
        List<PaymentRow> rows = new ArrayList<>();
        for (PaymentTransaction t : txns) {
            String date   = t.getPaymentDate() != null ? t.getPaymentDate().format(DT_FMT) : "";
            String method = t.getPaymentMethod() != null ? t.getPaymentMethod().getMethodName() : "";
            rows.add(new PaymentRow(date, method, formatMoney(t.getAmount())));
        }
        return rows;
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return NumberFormat.getNumberInstance(Locale.US).format(value.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String formatConditionsText(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            // Simple parse without ObjectMapper: extract name+status pairs via regex-free approach
            // JSON is: [{"name":"Screen","status":"Good"},...]
            StringBuilder sb = new StringBuilder();
            String[] entries = json.replace("[{", "").replace("}]", "").split("\\},\\{");
            for (String entry : entries) {
                String name = "";
                String status = "";
                for (String kv : entry.split(",")) {
                    kv = kv.trim().replace("\"", "");
                    if (kv.startsWith("name:")) name = kv.substring(5).trim();
                    else if (kv.startsWith("status:")) status = kv.substring(7).trim();
                }
                if (!status.isBlank()) {
                    if (sb.length() > 0) sb.append("  |  ");
                    sb.append(name).append(": ").append(status);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
