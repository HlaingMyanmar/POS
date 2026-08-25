package org.sspd.servicemgmt.bookingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.bookingoptions.dto.BookingAttachmentDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDetailDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDeviceDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDeviceInfoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingAttachment;
import org.sspd.servicemgmt.bookingoptions.model.BookingDetail;
import org.sspd.servicemgmt.bookingoptions.model.BookingDevice;
import org.sspd.servicemgmt.bookingoptions.model.BookingDeviceInfo;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingAttachmentRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;
import org.sspd.servicemgmt.shelflocationoptions.repository.ShelfLocationRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String BOOKING_TOPIC = "/topic/booking";

    private final BookingRepository bookingRepository;
    private final BookingAttachmentRepository attachmentRepository;
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final ShelfLocationRepository shelfLocationRepository;
    private final CompanySettingsService companySettingsService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CustomerPaymentService customerPaymentService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public Page<BookingDTO> findAll(String search, String dateFrom, String dateTo, int page, int size) {
        LocalDateTime from = parseDateStart(dateFrom);
        LocalDateTime to   = parseDateEnd(dateTo);
        return bookingRepository.findBySearchAndDate(search, from, to,
                        PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::toDto);
    }

    private LocalDateTime parseDateStart(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay();
    }

    private LocalDateTime parseDateEnd(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay().plusDays(1);
    }

    @Transactional(readOnly = true)
    public BookingDTO findById(Integer id) {
        return toDto(bookingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id)));
    }

    @Transactional(readOnly = true)
    public BookingDTO findByInvoiceNo(String invoiceNo) {
        return toDto(bookingRepository.findByInvoiceNo(invoiceNo)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + invoiceNo)));
    }

    @Transactional(readOnly = true)
    public List<BookingDTO> findByStatus(BookingStatus status) {
        return bookingRepository.findByStatus(status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDTO> findUpcoming(int minutesAhead) {
        LocalDateTime now = LocalDateTime.now();
        return bookingRepository.findUpcomingAppointments(now, now.plusMinutes(minutesAhead))
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public BookingDTO save(BookingDTO dto) {
        Customer customer = customerRepository.findById(dto.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        validateSerials(dto, null);

        Booking booking = Booking.builder()
            .invoiceNo(generateInvoiceNo())
            .customer(customer)
            .appointmentDate(dto.getAppointmentDate() != null && !dto.getAppointmentDate().isBlank()
                ? LocalDateTime.parse(dto.getAppointmentDate(), FMT) : null)
            .status(BookingStatus.Pending)
            .totalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO)
            .depositAmount(dto.getDepositAmount() != null ? dto.getDepositAmount() : BigDecimal.ZERO)
            .signatureData(dto.getSignatureData())
            .remark(dto.getRemark())
            .deviceType(dto.getDeviceType())
            .brand(dto.getBrand())
            .model(dto.getModel())
            .serialNumber(dto.getSerialNumber())
            .color(dto.getColor())
            .accessories(dto.getAccessories())
            .shelfLocation(dto.getShelfLocation())
            .build();

        if (dto.getPaymentMethodId() != null)
            booking.setPaymentMethod(paymentMethodRepository.findById(dto.getPaymentMethodId()).orElse(null));

        Staff staff = resolveReceiver(dto.getStaffId());
        validateReceiver(staff);
        booking.setStaff(staff);

        buildDeviceInfos(booking, dto);
        buildDevices(booking, dto);
        buildDetails(booking, dto);
        Booking saved = bookingRepository.save(booking);
        recordDeposit(saved, dto);
        BookingDTO result = toDto(bookingRepository.save(saved));
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_CREATED");
        return result;
    }

    @Transactional
    public BookingDTO update(Integer id, BookingDTO dto) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        if (booking.getStatus() == BookingStatus.Converted || booking.getStatus() == BookingStatus.Cancelled)
            throw new IllegalStateException("Converted or cancelled bookings cannot be edited");

        if (dto.getCustomerId() != null)
            booking.setCustomer(customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        Staff staff = resolveReceiver(dto.getStaffId());
        validateReceiver(staff);
        booking.setStaff(staff);
        if (dto.getAppointmentDate() != null && !dto.getAppointmentDate().isBlank())
            booking.setAppointmentDate(LocalDateTime.parse(dto.getAppointmentDate(), FMT));
        if (dto.getStatus() != null)
            booking.setStatus(dto.getStatus());

        validateSerials(dto, id);
        booking.setRemark(dto.getRemark());
        booking.setTotalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO);
        if (dto.getDepositAmount() != null) {
            BigDecimal currentDeposit = booking.getDepositAmount() != null ? booking.getDepositAmount() : BigDecimal.ZERO;
            if (booking.getAdvancePaymentId() != null && dto.getDepositAmount().compareTo(currentDeposit) != 0)
                throw new IllegalStateException("လက်ခံငွေ transaction ရှိပြီးဖြစ်၍ Booking မှ တိုက်ရိုက်ပြင်မရပါ။ Deposit ထပ်လက်ခံ/Refund transaction ကိုသုံးပါ။");
            booking.setDepositAmount(dto.getDepositAmount());
        }
        if (dto.getSignatureData() != null) booking.setSignatureData(dto.getSignatureData());
        if (dto.getPaymentMethodId() != null)
            booking.setPaymentMethod(paymentMethodRepository.findById(dto.getPaymentMethodId()).orElse(null));
        booking.setDeviceType(dto.getDeviceType());
        booking.setBrand(dto.getBrand());
        booking.setModel(dto.getModel());
        booking.setSerialNumber(dto.getSerialNumber());
        booking.setColor(dto.getColor());
        booking.setAccessories(dto.getAccessories());
        booking.setShelfLocation(dto.getShelfLocation());

        if (dto.getDeviceInfos() != null) {
            booking.getDeviceInfos().clear();
            buildDeviceInfos(booking, dto);
        }

        if (dto.getDevices() != null) {
            booking.getDevices().clear();
            buildDevices(booking, dto);
        }
        if (dto.getDetails() != null) {
            booking.getDetails().clear();
            buildDetails(booking, dto);
        }

        Booking saved = bookingRepository.save(booking);
        if (saved.getAdvancePaymentId() == null) recordDeposit(saved, dto);
        BookingDTO updated = toDto(bookingRepository.save(saved));
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_UPDATED");
        return updated;
    }

    private Staff resolveReceiver(Integer staffId) {
        if (staffId != null) return staffRepository.findById(staffId).orElse(null);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        User user = username == null ? null : userRepository.findByUsernameOrEmail(username, username).orElse(null);
        return user != null ? user.getStaff() : null;
    }

    private void validateReceiver(Staff selectedStaff) {
        if (hasAuthority("CAN_ACCESS_BOOKING_STAFF_OVERRIDE")) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        User user = username == null ? null : userRepository.findByUsernameOrEmail(username, username).orElse(null);
        if (user == null || user.getStaff() == null || selectedStaff == null
                || !user.getStaff().getId().equals(selectedStaff.getId())) {
            throw new AccessDeniedException("ပစ္စည်းလက်ခံမှုအတွက် သင့် Staff ကိုသာ ရွေးချယ်နိုင်ပါသည်။");
        }
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    @Transactional
    public BookingDTO updateStatus(Integer id, BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        BookingStatus from = booking.getStatus();
        if (from != status) {
            boolean allowed = switch (from) {
                case Pending -> status == BookingStatus.Confirmed || status == BookingStatus.IN_STORAGE || status == BookingStatus.Cancelled;
                case Confirmed -> status == BookingStatus.IN_STORAGE || status == BookingStatus.Cancelled;
                case IN_STORAGE -> status == BookingStatus.Cancelled;
                case Converted, Completed, Cancelled -> false;
            };
            if (!allowed) throw new IllegalStateException("Invalid booking status transition: " + from + " → " + status);
        }
        booking.setStatus(status);
        return toDto(bookingRepository.save(booking));
    }

    @Transactional
    public List<ServiceJobDTO> convertToJob(Integer bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.Converted)
            throw new IllegalStateException("Booking already converted to a service job");
        if (booking.getStatus() == BookingStatus.Cancelled)
            throw new IllegalStateException("Cannot convert a cancelled booking");
        if (booking.getStatus() == BookingStatus.Completed)
            throw new IllegalStateException("Cannot convert a completed booking");

        ShelfLocation shelfLocation = resolveShelfLocation(booking.getShelfLocation());

        // Build condition summary from device infos (shared across jobs)
        String itemCondition = "";
        if (booking.getDeviceInfos() != null && !booking.getDeviceInfos().isEmpty()) {
            itemCondition = booking.getDeviceInfos().stream().map(info -> {
                StringBuilder sb = new StringBuilder("[").append(info.getName()).append("]");
                if (info.getDescription() != null && !info.getDescription().isBlank())
                    sb.append(" ").append(info.getDescription());
                if (info.getStatus() != null && !info.getStatus().isBlank())
                    sb.append(" - ").append(info.getStatus());
                if (info.getNotice() != null && !info.getNotice().isBlank())
                    sb.append(" (").append(info.getNotice()).append(")");
                return sb.toString();
            }).collect(Collectors.joining("\n"));
        }

        List<ServiceJob> jobs = new ArrayList<>();

        if (booking.getDevices() != null && !booking.getDevices().isEmpty()) {
            // One Service Job per device
            for (BookingDevice device : booking.getDevices()) {
                String itemName = List.of(
                    device.getBrand()      != null ? device.getBrand()      : "",
                    device.getModel()      != null ? device.getModel()      : "",
                    device.getDeviceType() != null ? "(" + device.getDeviceType() + ")" : ""
                ).stream().filter(s -> !s.isBlank()).collect(Collectors.joining(" "));

                String devProblem = (device.getProblemDesc() != null && !device.getProblemDesc().isBlank())
                    ? device.getProblemDesc() : booking.getRemark();
                ServiceJob job = ServiceJob.builder()
                    .jobNo(generateJobNo())
                    .customer(booking.getCustomer())
                    .assignedStaff(booking.getStaff())
                    .itemName(itemName.isBlank() ? "Device" : itemName)
.itemCondition(device.getConditionChecklist() != null && !device.getConditionChecklist().isBlank()
                            ? device.getConditionChecklist() : itemCondition)
                    .deviceConditions(device.getDeviceConditions())
                    .serialNo(device.getSerialNumber())
                    .color(device.getColor())
                    .accessories(device.getAccessories())
                    .shelfLocation(shelfLocation)
                    .problemDesc(devProblem)
                    .estimatedCost(booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO)
                    .finalCost(BigDecimal.ZERO)
                    .status(ServiceJobStatus.RECEIVED)
                    .bookingId(bookingId)
                    .priority("NORMAL")
                    .lines(new ArrayList<>())
                    .build();
                applyBookingServices(job, booking);
                jobs.add(serviceJobRepository.save(job));
            }
        } else {
            // Legacy / no devices list — single job from booking fields
            String itemName = List.of(
                booking.getBrand()      != null ? booking.getBrand()      : "",
                booking.getModel()      != null ? booking.getModel()      : "",
                booking.getDeviceType() != null ? "(" + booking.getDeviceType() + ")" : ""
            ).stream().filter(s -> !s.isBlank()).collect(Collectors.joining(" "));

            ServiceJob job = ServiceJob.builder()
                .jobNo(generateJobNo())
                .customer(booking.getCustomer())
                .assignedStaff(booking.getStaff())
                .itemName(itemName.isBlank() ? "Device" : itemName)
                .itemCondition(itemCondition)
                .problemDesc(booking.getRemark())
                .serialNo(booking.getSerialNumber())
                .color(booking.getColor())
                .accessories(booking.getAccessories())
                .shelfLocation(shelfLocation)
                .estimatedCost(booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO)
                .finalCost(BigDecimal.ZERO)
                .status(ServiceJobStatus.RECEIVED)
                .bookingId(bookingId)
                .priority("NORMAL")
                .lines(new ArrayList<>())
                .build();
            applyBookingServices(job, booking);
            jobs.add(serviceJobRepository.save(job));
        }

        booking.setStatus(BookingStatus.Converted);
        bookingRepository.save(booking);
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_UPDATED");
        messagingTemplate.convertAndSend("/topic/service-jobs", "JOB_CREATED_FROM_BOOKING");

        return jobs.stream().map(this::toServiceJobDto).toList();
    }

    private ShelfLocation resolveShelfLocation(String code) {
        if (code == null || code.isBlank()) return null;
        String label = code.trim();
        String normalized = label.length() > 30 ? label.substring(0, 30) : label;
        return shelfLocationRepository.findByCodeIgnoreCase(normalized)
            .orElseGet(() -> shelfLocationRepository.save(ShelfLocation.builder()
                .code(normalized)
                .label(label)
                .active(true)
                .build()));
    }

    @Transactional
    public void delete(Integer id) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        if (booking.getStatus() == BookingStatus.Converted)
            throw new IllegalStateException("Job ပြောင်းပြီးသော လက်ခံမှတ်တမ်းကို ဖျက်မရပါ။");
        if (booking.getAdvancePaymentId() != null)
            throw new IllegalStateException("Deposit ရှိသော လက်ခံမှတ်တမ်းကို ဖျက်မရပါ။ Cancel လုပ်ပါ။");
        bookingRepository.deleteById(id);
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_DELETED");
    }

    @Transactional
    public BookingAttachmentDTO addAttachment(Integer bookingId, BookingAttachmentDTO dto) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (dto.getDataUrl() == null || dto.getDataUrl().isBlank())
            throw new IllegalArgumentException("Attachment data is required");
        if (dto.getDataUrl().length() > 4_500_000)
            throw new IllegalArgumentException("Attachment is too large");
        BookingAttachment saved = attachmentRepository.save(BookingAttachment.builder()
            .booking(booking)
            .attachmentType(dto.getAttachmentType() != null ? dto.getAttachmentType() : "INTAKE_PHOTO")
            .fileName(dto.getFileName())
            .contentType(dto.getContentType())
            .dataUrl(dto.getDataUrl())
            .uploadedBy(currentUsername())
            .uploadedAt(LocalDateTime.now())
            .build());
        return toAttachmentDto(saved);
    }

    @Transactional
    public void deleteAttachment(Integer bookingId, Integer attachmentId) {
        BookingAttachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        if (!attachment.getBooking().getId().equals(bookingId))
            throw new IllegalArgumentException("Attachment does not belong to this booking");
        attachmentRepository.delete(attachment);
    }

    private void applyBookingServices(ServiceJob job, Booking booking) {
        if (booking.getDetails() == null || booking.getDetails().isEmpty()) return;
        BigDecimal total = BigDecimal.ZERO;
        int minutes = 0;
        for (BookingDetail d : booking.getDetails()) {
            if (d.getServiceItem() == null) continue;
            int qty = d.getQty() != null ? d.getQty() : 1;
            BigDecimal price = d.getPrice() != null ? d.getPrice() : d.getServiceItem().getPrice();
            boolean foc = Boolean.TRUE.equals(d.getServiceItem().getFocDefault());
            BigDecimal sub = foc ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(qty));
            job.getLines().add(ServiceJobLine.builder()
                .serviceJob(job)
                .serviceItem(d.getServiceItem())
                .qty(qty)
                .price(price)
                .subtotal(sub)
                .warrantyMonths(d.getServiceItem().getWarrantyMonths() != null ? d.getServiceItem().getWarrantyMonths() : 0)
                .warrantyCovered(foc)
                .build());
            total = total.add(sub);
            minutes += (d.getServiceItem().getDurationMinutes() != null ? d.getServiceItem().getDurationMinutes() : 0) * qty;
        }
        if (total.compareTo(BigDecimal.ZERO) > 0) job.setEstimatedCost(total);
        if (minutes > 0) job.setEstimatedCompletion(LocalDateTime.now().plusMinutes(minutes));
    }

    private void recordDeposit(Booking booking, BookingDTO dto) {
        BigDecimal deposit = dto.getDepositAmount() != null ? dto.getDepositAmount() : booking.getDepositAmount();
        if (deposit == null || deposit.compareTo(BigDecimal.ZERO) <= 0) return;
        if (dto.getPaymentMethodId() == null)
            throw new IllegalArgumentException("လက်ခံငွေအတွက် ငွေပေးချေနည်း ရွေးပါ");
        CustomerPaymentDTO payment = new CustomerPaymentDTO();
        payment.setCustomerId(booking.getCustomer().getId());
        payment.setAmount(deposit);
        payment.setPaymentMethodId(dto.getPaymentMethodId());
        payment.setStaffId(booking.getStaff() != null ? booking.getStaff().getId() : dto.getStaffId());
        payment.setTransactionNo(dto.getTransactionNo());
        payment.setNote("Booking deposit " + booking.getInvoiceNo());
        CustomerPaymentDTO saved = customerPaymentService.createAdvancePayment(payment);
        booking.setAdvancePaymentId(saved.getId());
        booking.setDepositAmount(deposit);
    }

    private void validateSerials(BookingDTO dto, Integer excludeBookingId) {
        List<String> serials = new ArrayList<>();
        if (dto.getSerialNumber() != null && !dto.getSerialNumber().isBlank())
            serials.add(dto.getSerialNumber().trim());
        if (dto.getDevices() != null) {
            dto.getDevices().stream()
                .map(BookingDeviceDTO::getSerialNumber)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .forEach(serials::add);
        }
        long distinct = serials.stream().map(String::toLowerCase).distinct().count();
        if (distinct != serials.size())
            throw new IllegalArgumentException("Serial နံပါတ် ထပ်နေပါသည်။");
        for (String sn : serials) {
            if (bookingRepository.existsOpenSerial(sn, excludeBookingId))
                throw new IllegalArgumentException("Serial " + sn + " သည် လက်ခံဆဲ ပစ္စည်းတွင် ရှိပြီးသားဖြစ်သည်။");
            if (serviceJobRepository.existsOpenDeviceSerial(sn, null))
                throw new IllegalArgumentException("Serial " + sn + " သည် ပြင်ဆဲ Job တွင် ရှိပြီးသားဖြစ်သည်။");
        }
    }

    private void buildDetails(Booking booking, BookingDTO dto) {
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) return;
        for (BookingDetailDTO d : dto.getDetails()) {
            if (d.getServiceId() == null) continue;
            ServiceItem item = serviceItemRepository.findById(d.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + d.getServiceId()));
            int qty = d.getQty() != null ? d.getQty() : 1;
            BigDecimal price = d.getPrice() != null ? d.getPrice() : item.getPrice();
            booking.getDetails().add(BookingDetail.builder()
                .booking(booking)
                .serviceItem(item)
                .qty(qty)
                .price(price)
                .subtotal(price.multiply(BigDecimal.valueOf(qty)))
                .build());
        }
        BigDecimal serviceTotal = booking.getDetails().stream()
            .map(BookingDetail::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (serviceTotal.compareTo(BigDecimal.ZERO) > 0 && (dto.getTotalAmount() == null || dto.getTotalAmount().compareTo(BigDecimal.ZERO) == 0))
            booking.setTotalAmount(serviceTotal);
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private void buildDeviceInfos(Booking booking, BookingDTO dto) {
        if (dto.getDeviceInfos() == null || dto.getDeviceInfos().isEmpty()) return;
        for (BookingDeviceInfoDTO d : dto.getDeviceInfos()) {
            booking.getDeviceInfos().add(BookingDeviceInfo.builder()
                .booking(booking)
                .name(d.getName())
                .description(d.getDescription())
                .status(d.getStatus())
                .notice(d.getNotice())
                .build());
        }
    }

    private void buildDevices(Booking booking, BookingDTO dto) {
        if (dto.getDevices() == null || dto.getDevices().isEmpty()) return;
        for (BookingDeviceDTO d : dto.getDevices()) {
            boolean blank = isBlankStr(d.getDeviceType()) && isBlankStr(d.getBrand())
                && isBlankStr(d.getModel()) && isBlankStr(d.getSerialNumber())
                && isBlankStr(d.getColor()) && isBlankStr(d.getAccessories())
                && isBlankStr(d.getProblemDesc());
            if (blank) continue;
            booking.getDevices().add(BookingDevice.builder()
                .booking(booking)
                .deviceType(d.getDeviceType())
                .brand(d.getBrand())
                .model(d.getModel())
                .serialNumber(d.getSerialNumber())
                .color(d.getColor())
                .accessories(d.getAccessories())
                .problemDesc(d.getProblemDesc())
                .deviceConditions(d.getDeviceConditions())
                .conditionChecklist(d.getConditionChecklist())
                .build());
        }
    }

    private boolean isBlankStr(String s) { return s == null || s.isBlank(); }

    private String generateInvoiceNo() {
        int next = bookingRepository.findTopByOrderByIdDesc()
            .map(b -> b.getId() + 1).orElse(1);
        var cfg = companySettingsService.getSettings();
        String prefix = cfg.getBookingPrefix() != null && !cfg.getBookingPrefix().isBlank() ? cfg.getBookingPrefix() : "BK";
        int digits = cfg.getBookingDigits() != null ? cfg.getBookingDigits() : 6;
        return String.format("%s-%0" + digits + "d", prefix, next);
    }

    private String generateJobNo() {
        int next = serviceJobRepository.findTopByOrderByIdDesc()
            .map(j -> j.getId() + 1).orElse(1);
        return String.format("SJ-%06d", next);
    }

    private BookingDTO toDto(Booking b) {
        BookingDTO dto = new BookingDTO();
        dto.setId(b.getId());
        dto.setInvoiceNo(b.getInvoiceNo());
        dto.setCustomerId(b.getCustomer().getId());
        dto.setCustomerName(b.getCustomer().getName());
        dto.setCustomerPhone(b.getCustomer().getPhone());
        if (b.getStaff() != null) {
            dto.setStaffId(b.getStaff().getId());
            dto.setStaffName(b.getStaff().getName());
        }
        dto.setBookingDate(b.getBookingDate() != null ? b.getBookingDate().toString() : null);
        dto.setAppointmentDate(b.getAppointmentDate() != null ? b.getAppointmentDate().toString() : null);
        dto.setStatus(b.getStatus());
        dto.setTotalAmount(b.getTotalAmount());
        dto.setDepositAmount(b.getDepositAmount());
        dto.setAdvancePaymentId(b.getAdvancePaymentId());
        dto.setSignatureData(b.getSignatureData());
        dto.setRemark(b.getRemark());
        if (b.getPaymentMethod() != null) {
            dto.setPaymentMethodId(b.getPaymentMethod().getId());
            dto.setPaymentMethodName(b.getPaymentMethod().getMethodName());
        }
        dto.setDeviceType(b.getDeviceType());
        dto.setBrand(b.getBrand());
        dto.setModel(b.getModel());
        dto.setSerialNumber(b.getSerialNumber());
        dto.setColor(b.getColor());
        dto.setAccessories(b.getAccessories());
        dto.setShelfLocation(b.getShelfLocation());
        dto.setDeviceInfos(b.getDeviceInfos() != null
            ? b.getDeviceInfos().stream().map(d -> {
                BookingDeviceInfoDTO dd = new BookingDeviceInfoDTO();
                dd.setId(d.getId());
                dd.setName(d.getName());
                dd.setDescription(d.getDescription());
                dd.setStatus(d.getStatus());
                dd.setNotice(d.getNotice());
                return dd;
              }).toList()
            : List.of());
        dto.setDetails(b.getDetails() != null
            ? b.getDetails().stream().map(d -> {
                BookingDetailDTO dd = new BookingDetailDTO();
                dd.setId(d.getId());
                dd.setServiceId(d.getServiceItem() != null ? d.getServiceItem().getId() : null);
                dd.setServiceName(d.getServiceItem() != null ? d.getServiceItem().getItem() : null);
                dd.setQty(d.getQty());
                dd.setPrice(d.getPrice());
                dd.setSubtotal(d.getSubtotal());
                return dd;
              }).toList()
            : List.of());
        dto.setDevices(b.getDevices() != null
            ? b.getDevices().stream().map(d -> {
                BookingDeviceDTO dd = new BookingDeviceDTO();
                dd.setId(d.getId());
                dd.setDeviceType(d.getDeviceType());
                dd.setBrand(d.getBrand());
                dd.setModel(d.getModel());
                dd.setSerialNumber(d.getSerialNumber());
                dd.setColor(d.getColor());
                dd.setAccessories(d.getAccessories());
                dd.setProblemDesc(d.getProblemDesc());
                dd.setDeviceConditions(d.getDeviceConditions());
                dd.setConditionChecklist(d.getConditionChecklist());
                return dd;
              }).toList()
            : List.of());
        dto.setAttachments(attachmentRepository.findByBookingIdOrderByUploadedAtDesc(b.getId()).stream()
            .map(this::toAttachmentDto).toList());
        return dto;
    }

    private BookingAttachmentDTO toAttachmentDto(BookingAttachment a) {
        BookingAttachmentDTO dto = new BookingAttachmentDTO();
        dto.setId(a.getId());
        dto.setAttachmentType(a.getAttachmentType());
        dto.setFileName(a.getFileName());
        dto.setContentType(a.getContentType());
        dto.setDataUrl(a.getDataUrl());
        dto.setUploadedBy(a.getUploadedBy());
        dto.setUploadedAt(a.getUploadedAt());
        return dto;
    }

    private ServiceJobDTO toServiceJobDto(ServiceJob j) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setId(j.getId());
        dto.setJobNo(j.getJobNo());
        dto.setCustomerId(j.getCustomer().getId());
        dto.setCustomerName(j.getCustomer().getName());
        if (j.getAssignedStaff() != null) {
            dto.setAssignedStaffId(j.getAssignedStaff().getId());
            dto.setAssignedStaffName(j.getAssignedStaff().getName());
        }
        dto.setItemName(j.getItemName());
        dto.setItemCondition(j.getItemCondition());
        dto.setDeviceConditions(j.getDeviceConditions());
        dto.setSerialNo(j.getSerialNo());
        dto.setColor(j.getColor());
        dto.setAccessories(j.getAccessories());
        dto.setProblemDesc(j.getProblemDesc());
        dto.setStatus(j.getStatus());
        dto.setBookingId(j.getBookingId());
        dto.setBookingNo(j.getBookingId() != null ? bookingRepository.findById(j.getBookingId()).map(Booking::getInvoiceNo).orElse(null) : null);
        dto.setReceivedDate(j.getReceivedDate() != null ? j.getReceivedDate().toString() : null);
        dto.setEstimatedCost(j.getEstimatedCost());
        dto.setFinalCost(j.getFinalCost());
        dto.setLines(List.of());
        return dto;
    }
}
