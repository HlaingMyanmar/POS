package org.sspd.servicemgmt.printingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingDevice;
import org.sspd.servicemgmt.bookingoptions.model.BookingDetail;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.printingoptions.dto.PrintInvoiceData;
import org.sspd.servicemgmt.printingoptions.dto.PrintLineItem;
import org.sspd.servicemgmt.printingoptions.dto.PrintPageConfig;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest;
import org.sspd.servicemgmt.printingoptions.entity.VoucherSetting;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Assembles a {@link PrintInvoiceData} from the database entities matching
 * the incoming {@link PrintRequest}.  One source-of-truth for field mapping
 * so both the PDF endpoint and the HTML-preview endpoint share the same data.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceAssemblerService {

    private final SaleRepository saleRepository;
    private final BookingRepository bookingRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CompanySettingsService companySettingsService;
    private final QrCodeService qrCodeService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Public dispatch ───────────────────────────────────────────────────────

    public PrintInvoiceData assemble(PrintRequest req) {
        return assemble(req, null);
    }

    /**
     * Assembles invoice data, then overlays non-null fields from {@code setting}.
     * Pass {@code null} for setting to use only the request and company defaults.
     */
    public PrintInvoiceData assemble(PrintRequest req, VoucherSetting setting) {
        PrintPageConfig pageCfg = resolvePageConfig(req.getPaperSize());
        CompanySettingsDTO cs = companySettingsService.getSettings();

        PrintInvoiceData data = switch (req.getDocumentType()) {
            case SALE         -> assembleSale(req.getDocumentId(), cs);
            case BOOKING      -> assembleBooking(req.getDocumentId(), cs);
            case SERVICE_JOB  -> assembleServiceJob(req.getDocumentId(), cs);
            case SERVICE_DONE -> assembleServiceDone(req.getDocumentId(), cs);
            case PURCHASE     -> assemblePurchase(req.getDocumentId(), cs);
        };
        data.setDocumentType(req.getDocumentType().name());
        data.setCopyLabel("SHOP".equalsIgnoreCase(req.getCopyType()) ? "SHOP COPY" : "CUSTOMER COPY");

        data.setPageConfig(pageCfg);
        data.setShowLogo(req.isShowLogo());
        data.setShowSerial(req.isShowSerial());
        data.setShowPaymentHistory(req.isShowPaymentHistory());
        data.setShowSignatures(req.isShowSignatures());
        data.setShowQrCode(req.isShowQrCode());
        data.setSign1Label(req.getSign1Label());
        data.setSign2Label(req.getSign2Label());
        data.setHeaderFontFamily(req.getHeaderFontFamily());
        data.setHeaderFontSizePx(req.getHeaderFontSizePx());
        data.setInfoFontFamily(req.getInfoFontFamily());
        data.setInfoFontSizePx(req.getInfoFontSizePx());
        data.setTableHeaderFontFamily(req.getTableHeaderFontFamily());
        data.setTableHeaderFontSizePx(req.getTableHeaderFontSizePx());
        data.setTableDataFontFamily(req.getTableDataFontFamily());
        data.setTableDataFontSizePx(req.getTableDataFontSizePx());
        data.setFooterFontFamily(req.getFooterFontFamily());
        data.setFooterFontSizePx(req.getFooterFontSizePx());
        data.setNoticeFontFamily(req.getNoticeFontFamily());
        data.setNoticeFontSizePx(req.getNoticeFontSizePx());

        if (req.isShowQrCode()) {
            String qrContent = buildQrContent(data);
            data.setQrCodeBase64(qrCodeService.generateDataUri(qrContent));
        }

        if (setting != null) {
            if (setting.getVoucherTitle() != null && !setting.getVoucherTitle().isBlank())
                data.setInvoiceTitle(setting.getVoucherTitle());
            if (setting.getFooterNote() != null && !setting.getFooterNote().isBlank())
                data.setFooterNote(setting.getFooterNote());
            if (setting.getCustomerNotice() != null && !setting.getCustomerNotice().isBlank())
                data.setCustomerNotice(setting.getCustomerNotice());
        }

        return data;
    }

    // ── Sale ─────────────────────────────────────────────────────────────────

    private PrintInvoiceData assembleSale(Integer saleId, CompanySettingsDTO cs) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NoSuchElementException("Sale not found: " + saleId));

        List<PrintLineItem> items = new ArrayList<>();
        BigDecimal voucherTotal = BigDecimal.ZERO;
        BigDecimal voucherLineDiscount = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        boolean hasCustomVoucherPrice = false;
        if (sale.getDetails() != null) {
            int i = 1;
            for (SaleDetail d : sale.getDetails()) {
                String serial = d.getSerialNumber() != null ? d.getSerialNumber() : "";
                boolean useCustomVoucherPrice = d.getCustomVoucherPrice() != null
                        && d.getCustomVoucherPrice().compareTo(BigDecimal.ZERO) > 0;
                BigDecimal displayUnitPrice = useCustomVoucherPrice ? d.getCustomVoucherPrice() : d.getUnitPrice();
                BigDecimal displaySubtotal = useCustomVoucherPrice
                        ? displayUnitPrice.multiply(BigDecimal.valueOf(d.getQty() != null ? d.getQty() : 0))
                        : d.getSubtotal();
                hasCustomVoucherPrice |= useCustomVoucherPrice;
                voucherTotal = voucherTotal.add(displaySubtotal != null ? displaySubtotal : BigDecimal.ZERO);
                voucherLineDiscount = voucherLineDiscount.add(d.getDiscountAmount() != null ? d.getDiscountAmount() : BigDecimal.ZERO);
                commission = commission.add(d.getCustomerMargin() != null ? d.getCustomerMargin() : BigDecimal.ZERO);
                items.add(PrintLineItem.builder()
                        .rowNo(i++)
                        .productName(safe(d.getProduct() != null ? d.getProduct().getName() : ""))
                        .serialInfo(serial)
                        .qty(d.getQty() != null ? d.getQty() : 0)
                        .unitPrice(fmt(displayUnitPrice))
                        .subtotal(fmt(displaySubtotal))
                        .discount(fmt(d.getDiscountAmount()))
                        .foc(Boolean.TRUE.equals(d.getFoc()))
                        .warrantyLabel(fmtWarrantyLabel(d.getWarrantyMonths(), d.getWarrantyExpiryDate()))
                        .build());
            }
        }

        List<PrintInvoiceData.PaymentEntry> payments = buildPayments(saleId, ReferenceType.Sale);

        return PrintInvoiceData.builder()
                .companyName(cs.getCompanyName())
                .companyAddress(safe(cs.getCompanyAddress()))
                .companyPhone(safe(cs.getCompanyPhone()))
                .companyEmail(safe(cs.getCompanyEmail()))
                .logoBase64(safe(cs.getLogoBase64()))
                .footerNote(safe(cs.getFooterNote()))
                .headerColor("#1e3a5f")
                .invoiceTitle(safe(cs.getInvoiceTitle(), "SALES INVOICE"))
                .invoiceNo(sale.getSaleCode())
                .invoiceDate(sale.getSaleDate() != null ? sale.getSaleDate().format(DT_FMT) : "")
                .dueDate(sale.getDueDate() != null ? sale.getDueDate().format(D_FMT) : "")
                .paymentStatus(sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : "")
                .creditStatus("")
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getName() : "")
                .customerPhone(sale.getCustomer() != null ? safe(sale.getCustomer().getPhone()) : "")
                .customerAddress(sale.getCustomer() != null ? safe(sale.getCustomer().getAddress()) : "")
                .cashierName(sale.getStaff() != null ? sale.getStaff().getName() : "")
                .lineItems(items)
                .payments(payments)
                .subtotal(fmt(hasCustomVoucherPrice ? voucherTotal : sale.getTotalAmount()))
                .discount(fmt(hasCustomVoucherPrice ? voucherLineDiscount.add(sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO) : sale.getDiscountAmount()))
                .tax(fmt(sale.getTaxAmount()))
                .netAmount(fmt(hasCustomVoucherPrice ? voucherTotal.subtract(voucherLineDiscount.add(sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO)).max(BigDecimal.ZERO).add(sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO) : sale.getNetAmount()))
                .commission(fmt(commission))
                .paid(fmt(hasCustomVoucherPrice ? voucherTotal.subtract(voucherLineDiscount.add(sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO)).max(BigDecimal.ZERO).add(sale.getTaxAmount() != null ? sale.getTaxAmount() : BigDecimal.ZERO) : sale.getPaidAmount()))
                .balanceDue(fmt(hasCustomVoucherPrice ? BigDecimal.ZERO : sale.getDueAmount()))
                .remark(safe(sale.getRemark()))
                .customerNotice("")
                .build();
    }

    // ── Booking ───────────────────────────────────────────────────────────────

    private PrintInvoiceData assembleBooking(Integer bookingId, CompanySettingsDTO cs) {
        Booking b = bookingRepository.findByIdWithDevices(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));

        // Build device rows — prefer the devices list; fall back to single device fields
        List<PrintInvoiceData.DeviceRow> deviceRows = new ArrayList<>();
        if (b.getDevices() != null && !b.getDevices().isEmpty()) {
            int deviceCount = b.getDevices().size();
            for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
                BookingDevice d = b.getDevices().get(deviceIndex);
                deviceRows.add(PrintInvoiceData.DeviceRow.builder()
                        .deviceType(safe(d.getDeviceType()))
                        .brand(safe(d.getBrand()))
                        .model(safe(d.getModel()))
                        .serialNo(safe(d.getSerialNumber()))
                        .color(safe(d.getColor()))
                        .accessories(safe(d.getAccessories()))
                        .problemDesc(safe(d.getProblemDesc()))
                        .deviceConditions(safe(d.getDeviceConditions()))
                        .serviceSummary(formatBookingServices(b, deviceIndex, deviceCount))
                        .conditionChecklist(formatConditionChecklist(d.getConditionChecklist()))
                        .partRequests(formatPartRequests(d.getPartRequests()))
                        .build());
            }
        } else if (!safe(b.getBrand()).isBlank() || !safe(b.getModel()).isBlank()
                || !safe(b.getDeviceType()).isBlank() || !safe(b.getSerialNumber()).isBlank()
                || !safe(b.getColor()).isBlank() || !safe(b.getAccessories()).isBlank()) {
            deviceRows.add(PrintInvoiceData.DeviceRow.builder()
                    .deviceType(safe(b.getDeviceType()))
                    .brand(safe(b.getBrand()))
                    .model(safe(b.getModel()))
                    .serialNo(safe(b.getSerialNumber()))
                    .color(safe(b.getColor()))
                    .accessories(safe(b.getAccessories()))
                    .problemDesc("")
                    .deviceConditions("")
                    .serviceSummary(formatBookingServices(b, 0, 1))
                    .conditionChecklist("")
                    .partRequests("")
                    .build());
        }

        return PrintInvoiceData.builder()
                .companyName(cs.getCompanyName())
                .companyAddress(safe(cs.getCompanyAddress()))
                .companyPhone(safe(cs.getCompanyPhone()))
                .companyEmail(safe(cs.getCompanyEmail()))
                .logoBase64(safe(cs.getLogoBase64()))
                .footerNote(safe(cs.getFooterNote()))
                .headerColor("#1e3a5f")
                .invoiceTitle("DEVICE INTAKE RECEIPT")
                .invoiceNo(safe(b.getInvoiceNo()))
                .invoiceDate(b.getBookingDate() != null ? b.getBookingDate().format(DT_FMT) : "")
                .dueDate(b.getAppointmentDate() != null ? b.getAppointmentDate().format(DT_FMT) : "")
                .paymentStatus(b.getStatus() != null ? b.getStatus().name() : "")
                .customerName(b.getCustomer() != null ? b.getCustomer().getName() : "")
                .customerPhone(b.getCustomer() != null ? safe(b.getCustomer().getPhone()) : "")
                .cashierName(b.getStaff() != null ? b.getStaff().getName() : "")
                .shelfLocation(safe(b.getShelfLocation()))
                .estimatedCost(b.getTotalAmount() != null ? fmt(b.getTotalAmount()) : "0")
                .paid(b.getDepositAmount() != null ? fmt(b.getDepositAmount()) : "0")
                .lineItems(List.of())
                .payments(List.of())
                .subtotal("0")
                .discount("0")
                .netAmount(b.getTotalAmount() != null ? fmt(b.getTotalAmount()) : "0")
                .balanceDue("0")
                .remark(safe(b.getRemark()))
                .bookingReceipt(true)
                .deviceRows(deviceRows)
                .build();
    }

    // ── Service Job ───────────────────────────────────────────────────────────

    private PrintInvoiceData assembleServiceJob(Integer jobId, CompanySettingsDTO cs) {
        ServiceJob job = serviceJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("ServiceJob not found: " + jobId));

        List<PrintLineItem> items = new ArrayList<>();
        int i = 1;
        String serviceDevice = safe(job.getItemName());
        String serviceSerial = safe(job.getSerialNo());
        String serviceDeviceInfo = serviceDevice
                + (serviceSerial.isBlank() ? "" : " / S/N: " + serviceSerial);
        if (serviceDeviceInfo.isBlank()) serviceDeviceInfo = "Device not specified";
        if (job.getLines() != null) {
            for (ServiceJobLine l : job.getLines()) {
                items.add(PrintLineItem.builder()
                        .rowNo(i++)
                        .productName(l.getServiceItem() != null ? l.getServiceItem().getItem() : "")
                        .serialInfo(serviceDeviceInfo)
                        .qty(l.getQty() != null ? l.getQty() : 0)
                        .unitPrice(fmt(l.getPrice()))
                        .subtotal(fmt(l.getSubtotal()))
                        .discount("0")
                        .warrantyLabel(fmtWarrantyLabel(l.getWarrantyMonths(), null))
                        .build());
            }
        }
        if (job.getProductParts() != null) {
            for (ServiceJobPart p : job.getProductParts()) {
                items.add(PrintLineItem.builder()
                        .rowNo(i++)
                        .productName(p.getProduct() != null ? p.getProduct().getName() : "")
                        .serialInfo(safe(p.getSerialNumbers()))
                        .qty(p.getQty() != null ? p.getQty() : 0)
                        .unitPrice(fmt(p.getUnitPrice()))
                        .subtotal(fmt(p.getSubtotal()))
                        .discount("0")
                        .warrantyLabel("")
                        .build());
            }
        }

        BigDecimal laborTotal = job.getLines() == null ? BigDecimal.ZERO :
                job.getLines().stream().map(ServiceJobLine::getSubtotal)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal partsTotal = job.getProductParts() == null ? BigDecimal.ZERO :
                job.getProductParts().stream().map(ServiceJobPart::getSubtotal)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = laborTotal.add(partsTotal);

        List<PrintInvoiceData.PaymentEntry> payments = new ArrayList<>(buildPayments(jobId, ReferenceType.Service));
        if (job.getSaleId() != null) {
            payments.addAll(buildPayments(job.getSaleId(), ReferenceType.Sale));
        }

        // Accessories: entity's own value first, then booking fallback
        String accessories = safe(job.getAccessories());
        if (accessories.isBlank() && job.getBookingId() != null) {
            accessories = bookingRepository.findById(job.getBookingId())
                    .map(b -> safe(b.getAccessories())).orElse("");
        }

        // Parse device conditions JSON → ConditionRow list
        List<PrintInvoiceData.ConditionRow> conditionRows = parseConditionRows(job.getDeviceConditions());

        return PrintInvoiceData.builder()
                .companyName(cs.getCompanyName())
                .companyAddress(safe(cs.getCompanyAddress()))
                .companyPhone(safe(cs.getCompanyPhone()))
                .companyEmail(safe(cs.getCompanyEmail()))
                .logoBase64(safe(cs.getLogoBase64()))
                .footerNote(safe(cs.getFooterNote()))
                .headerColor("#1e3a5f")
                .invoiceTitle("SERVICE VOUCHER")
                .invoiceNo(safe(job.getJobNo()))
                .invoiceDate(job.getReceivedDate() != null ? job.getReceivedDate().format(DT_FMT) : "")
                .dueDate(job.getCompletedDate() != null ? job.getCompletedDate().format(DT_FMT) : "")
                .paymentStatus(job.getPaymentStatus() != null ? job.getPaymentStatus().name() : "")
                .customerName(job.getCustomer() != null ? job.getCustomer().getName() : "")
                .customerPhone(job.getCustomer() != null ? safe(job.getCustomer().getPhone()) : "")
                .cashierName(currentCashierName())
                .technicianName(staffName(job.getAssignedStaff()))
                .helperStaffName(staffName(job.getHelperStaff()))
                .lineItems(items)
                .payments(payments)
                .subtotal(fmt(gross))
                .discount(fmt(job.getDiscountAmount()))
                .netAmount(fmt(job.getNetAmount()))
                .paid(fmt(job.getPaidAmount()))
                .balanceDue(fmt(job.getDueAmount()))
                .remark(safe(job.getRemark()))
                .itemName(safe(job.getItemName()))
                .problemDesc(safe(job.getProblemDesc()))
                .accessories(accessories)
                .partRequests(formatPartRequests(job.getPartRequests()))
                .estimatedCost(job.getEstimatedCost() != null && job.getEstimatedCost().compareTo(BigDecimal.ZERO) > 0
                        ? fmt(job.getEstimatedCost()) : "")
                .deviceConditionRows(conditionRows)
                .build();
    }

    private String formatBookingServices(Booking booking, int deviceIndex, int deviceCount) {
        if (booking.getDetails() == null) return "";
        List<String> rows = new ArrayList<>();
        for (BookingDetail detail : booking.getDetails()) {
            Integer targetIndex = detail.getDeviceIndex();
            boolean applies = targetIndex != null
                    ? targetIndex == deviceIndex
                    : deviceCount == 1 || deviceIndex == 0;
            if (!applies || detail.getServiceItem() == null) continue;
            int qty = detail.getQty() != null ? detail.getQty() : 1;
            String price = detail.getSubtotal() != null ? fmt(detail.getSubtotal()) : fmt(detail.getPrice());
            rows.add(detail.getServiceItem().getItem() + " x " + qty + " - " + price + " Ks");
        }
        return String.join("\n", rows);
    }

    private String formatConditionChecklist(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> rows = new ArrayList<>();
            for (Map<String, Object> item : parsed) {
                String name = safe(Objects.toString(item.get("name"), ""));
                if (name.isBlank()) continue;
                String status = switch (safe(Objects.toString(item.get("status"), ""))) {
                    case "Good" -> "Good";
                    case "Damaged" -> "Damaged";
                    case "Check Required" -> "Check required";
                    default -> safe(Objects.toString(item.get("status"), ""));
                };
                String notice = safe(Objects.toString(item.get("notice"), ""));
                rows.add(name + (status.isBlank() ? "" : " - " + status)
                        + (notice.isBlank() ? "" : " (" + notice + ")"));
            }
            return String.join("\n", rows);
        } catch (Exception ignored) {
            return safe(json);
        }
    }

    private String formatPartRequests(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> rows = new ArrayList<>();
            for (Map<String, Object> item : parsed) {
                String name = safe(Objects.toString(item.get("partName"), ""));
                if (name.isBlank()) continue;
                String action = switch (safe(Objects.toString(item.get("action"), ""))) {
                    case "REPLACE" -> "Replace";
                    case "REPAIR" -> "Repair";
                    case "CHECK" -> "Check";
                    default -> safe(Objects.toString(item.get("action"), ""));
                };
                String qty = safe(Objects.toString(item.get("qty"), "1"));
                String notice = safe(Objects.toString(item.get("notice"), ""));
                rows.add(name + (action.isBlank() ? "" : " - " + action) + " x " + qty
                        + (notice.isBlank() ? "" : " (" + notice + ")"));
            }
            return String.join("\n", rows);
        } catch (Exception ignored) {
            return safe(json);
        }
    }
    private List<PrintInvoiceData.ConditionRow> parseConditionRows(String json) {
        List<PrintInvoiceData.ConditionRow> rows = new ArrayList<>();
        if (json == null || json.isBlank()) return rows;
        try {
            List<Map<String, String>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, String> m : parsed) {
                String status = m.get("status");
                if (status != null && !status.isBlank()) {
                    rows.add(PrintInvoiceData.ConditionRow.builder()
                            .component(safe(m.get("name")))
                            .status(status)
                            .build());
                }
            }
        } catch (Exception ignored) {}
        return rows;
    }

    // ── Service Done ──────────────────────────────────────────────────────────

    private PrintInvoiceData assembleServiceDone(Integer jobId, CompanySettingsDTO cs) {
        PrintInvoiceData data = assembleServiceJob(jobId, cs);
        data.setInvoiceTitle("SERVICE DONE VOUCHER");
        return data;
    }

    // ── Purchase ──────────────────────────────────────────────────────────────

    private PrintInvoiceData assemblePurchase(Integer purchaseId, CompanySettingsDTO cs) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new NoSuchElementException("Purchase not found: " + purchaseId));

        List<PrintLineItem> items = new ArrayList<>();
        if (purchase.getDetails() != null) {
            int i = 1;
            for (PurchaseDetail d : purchase.getDetails()) {
                items.add(PrintLineItem.builder()
                        .rowNo(i++)
                        .productName(d.getProduct() != null ? d.getProduct().getName() : "")
                        .serialInfo("")
                        .qty(d.getQty() != null ? d.getQty() : 0)
                        .unitPrice(fmt(d.getUnitCost()))
                        .subtotal(fmt(d.getSubtotal()))
                        .discount("0")
                        .warrantyLabel(fmtWarrantyLabel(d.getWarrantyMonths(), null))
                        .build());
            }
        }

        List<PrintInvoiceData.PaymentEntry> purchasePayments = buildPayments(purchaseId, ReferenceType.Purchase);
        BigDecimal discount = nz(purchase.getDiscountAmount());
        BigDecimal tax = nz(purchase.getTaxAmount());
        BigDecimal other = nz(purchase.getOtherCharges());
        BigDecimal net = purchase.getNetAmount() != null
                ? purchase.getNetAmount()
                : nz(purchase.getTotalAmount()).subtract(discount).add(tax).add(other);
        BigDecimal due = purchase.getDueAmount() != null
                ? purchase.getDueAmount().max(BigDecimal.ZERO)
                : net.subtract(nz(purchase.getPaidAmount())).max(BigDecimal.ZERO);

        return PrintInvoiceData.builder()
                .companyName(cs.getCompanyName())
                .companyAddress(safe(cs.getCompanyAddress()))
                .companyPhone(safe(cs.getCompanyPhone()))
                .companyEmail(safe(cs.getCompanyEmail()))
                .logoBase64(safe(cs.getLogoBase64()))
                .footerNote(safe(cs.getFooterNote()))
                .headerColor("#1e3a5f")
                .invoiceTitle(safe(cs.getInvoiceTitle(), "PURCHASE ORDER"))
                .invoiceNo(purchase.getPurchaseCode())
                .invoiceDate(purchase.getPurchaseDate() != null ? purchase.getPurchaseDate().format(DT_FMT) : "")
                .dueDate(purchase.getDueDate() != null ? purchase.getDueDate().format(D_FMT) : "")
                .paymentStatus(purchase.getPaymentStatus() != null ? purchase.getPaymentStatus().name() : "")
                .creditStatus("")
                .customerName(purchase.getSupplier() != null ? purchase.getSupplier().getName() : "")
                .customerPhone(purchase.getSupplier() != null ? safe(purchase.getSupplier().getPhone()) : "")
                .customerAddress(purchase.getSupplier() != null ? safe(purchase.getSupplier().getAddress()) : "")
                .cashierName(purchase.getStaff() != null ? purchase.getStaff().getName() : "")
                .lineItems(items)
                .payments(purchasePayments)
                .subtotal(fmt(purchase.getTotalAmount()))
                .discount(fmt(discount))
                .tax(fmt(tax))
                .otherCharges(fmt(other))
                .netAmount(fmt(net))
                .paid(fmt(purchase.getPaidAmount()))
                .balanceDue(fmt(due))
                .remark(safe(purchase.getRemark()))
                .customerNotice("")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<PrintInvoiceData.PaymentEntry> buildPayments(Integer refId, ReferenceType type) {
        List<PaymentTransaction> txns =
                paymentTransactionRepository.findByReferenceIdAndReferenceType(refId, type);
        List<PrintInvoiceData.PaymentEntry> rows = new ArrayList<>();
        for (PaymentTransaction t : txns) {
            String methodName = t.getPaymentMethod() != null ? t.getPaymentMethod().getMethodName() : "";
            String txnNo = t.getTransactionNo();
            String displayMethod = (txnNo != null && !txnNo.isBlank())
                    ? methodName + "||" + txnNo
                    : methodName;
            rows.add(PrintInvoiceData.PaymentEntry.builder()
                    .date(t.getPaymentDate() != null ? t.getPaymentDate().format(DT_FMT) : "")
                    .method(displayMethod)
                    .amount(fmt(t.getAmount()))
                    .build());
        }
        return rows;
    }

    private PrintPageConfig resolvePageConfig(String paperSize) {
        if (paperSize == null) return PrintPageConfig.a4();
        return switch (paperSize.toUpperCase()) {
            case "A5"        -> PrintPageConfig.a5();
            case "POS_58MM"  -> PrintPageConfig.pos58mm();
            case "POS_80MM"  -> PrintPageConfig.pos80mm();
            default          -> PrintPageConfig.a4();
        };
    }

    private String fmt(BigDecimal v) {
        if (v == null) return "0";
        return NumberFormat.getNumberInstance(Locale.US)
                .format(v.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String safe(String s) { return s != null ? s : ""; }

    private String safe(String s, String fallback) { return (s != null && !s.isBlank()) ? s : fallback; }

    private String staffName(Staff staff) {
        return staff != null ? safe(staff.getName()) : "";
    }

    /** Logged-in staff (or user name) who printed / collected payment — not the technician. */
    private String currentCashierName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return "";
        }
        String username = authentication.getName();
        return userRepository.findByUsernameOrEmail(username, username)
                .map(this::userDisplayName)
                .orElse("");
    }

    private String userDisplayName(User user) {
        if (user.getStaff() != null && !safe(user.getStaff().getName()).isBlank()) {
            return user.getStaff().getName();
        }
        if (!safe(user.getName()).isBlank()) return user.getName();
        return safe(user.getUsername());
    }

    /** Converts warranty months → "N Year(s)" / "N Month(s)", optionally appending expiry. */
    private String fmtWarrantyLabel(Integer months, java.time.LocalDate expiryDate) {
        int m = months != null ? months : 0;
        String duration = "";
        if (m > 0) {
            if (m % 12 == 0) {
                int y = m / 12;
                duration = y + (y == 1 ? " Year" : " Years");
            } else {
                duration = m + (m == 1 ? " Month" : " Months");
            }
        }
        if (duration.isEmpty()) return "";
        if (expiryDate != null) return duration + " (exp: " + expiryDate.format(D_FMT) + ")";
        return duration;
    }

    private String buildQrContent(PrintInvoiceData d) {
        StringBuilder sb = new StringBuilder();
        if (d.getInvoiceNo()   != null) sb.append(d.getInvoiceNo());
        if (d.getInvoiceDate() != null) sb.append("\n").append(d.getInvoiceDate());
        if (d.getCustomerName()!= null) sb.append("\n").append(d.getCustomerName());
        if (d.getNetAmount()   != null) sb.append("\nAmt: ").append(d.getNetAmount());
        return sb.toString();
    }
}
