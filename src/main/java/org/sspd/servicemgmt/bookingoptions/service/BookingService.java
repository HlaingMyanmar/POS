package org.sspd.servicemgmt.bookingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDeviceDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDeviceInfoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingDevice;
import org.sspd.servicemgmt.bookingoptions.model.BookingDeviceInfo;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
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
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final CompanySettingsService companySettingsService;
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

        Booking booking = Booking.builder()
            .invoiceNo(generateInvoiceNo())
            .customer(customer)
            .appointmentDate(dto.getAppointmentDate() != null
                ? LocalDateTime.parse(dto.getAppointmentDate(), FMT) : null)
            .status(BookingStatus.Pending)
            .totalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO)
            .remark(dto.getRemark())
            .deviceType(dto.getDeviceType())
            .brand(dto.getBrand())
            .model(dto.getModel())
            .serialNumber(dto.getSerialNumber())
            .color(dto.getColor())
            .accessories(dto.getAccessories())
            .shelfLocation(dto.getShelfLocation())
            .build();

        if (dto.getStaffId() != null)
            booking.setStaff(staffRepository.findById(dto.getStaffId()).orElse(null));

        buildDeviceInfos(booking, dto);
        buildDevices(booking, dto);
        BookingDTO result = toDto(bookingRepository.save(booking));
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
        if (dto.getStaffId() != null)
            booking.setStaff(staffRepository.findById(dto.getStaffId()).orElse(null));
        else
            booking.setStaff(null);
        if (dto.getAppointmentDate() != null)
            booking.setAppointmentDate(LocalDateTime.parse(dto.getAppointmentDate(), FMT));
        if (dto.getStatus() != null)
            booking.setStatus(dto.getStatus());

        booking.setRemark(dto.getRemark());
        booking.setTotalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO);
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

        BookingDTO updated = toDto(bookingRepository.save(booking));
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_UPDATED");
        return updated;
    }

    @Transactional
    public BookingDTO updateStatus(Integer id, BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
        booking.setStatus(status);
        return toDto(bookingRepository.save(booking));
    }

    @Transactional
    public List<ServiceJobDTO> convertToJob(Integer bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.Converted)
            throw new IllegalStateException("Booking already converted to a service job");
        if (booking.getStatus() == BookingStatus.Cancelled)
            throw new IllegalStateException("Cannot convert a cancelled booking");
        if (booking.getStatus() == BookingStatus.Completed)
            throw new IllegalStateException("Cannot convert a completed booking");

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
                    .itemCondition(itemCondition)
                    .deviceConditions(device.getDeviceConditions())
                    .problemDesc(devProblem)
                    .accessories(device.getAccessories())
                    .estimatedCost(booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO)
                    .finalCost(BigDecimal.ZERO)
                    .status(ServiceJobStatus.RECEIVED)
                    .bookingId(bookingId)
                    .rework(false)
                    .lines(new ArrayList<>())
                    .build();
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
                .accessories(booking.getAccessories())
                .estimatedCost(booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO)
                .finalCost(BigDecimal.ZERO)
                .status(ServiceJobStatus.RECEIVED)
                .bookingId(bookingId)
                .rework(false)
                .lines(new ArrayList<>())
                .build();
            jobs.add(serviceJobRepository.save(job));
        }

        booking.setStatus(BookingStatus.Converted);
        bookingRepository.save(booking);
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_UPDATED");

        return jobs.stream().map(this::toServiceJobDto).toList();
    }

    @Transactional
    public void delete(Integer id) {
        bookingRepository.deleteById(id);
        messagingTemplate.convertAndSend(BOOKING_TOPIC, "BOOKING_DELETED");
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
        dto.setRemark(b.getRemark());
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
        dto.setDetails(List.of());
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
                return dd;
              }).toList()
            : List.of());
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
        dto.setProblemDesc(j.getProblemDesc());
        dto.setStatus(j.getStatus());
        dto.setBookingId(j.getBookingId());
        dto.setEstimatedCost(j.getEstimatedCost());
        dto.setFinalCost(j.getFinalCost());
        dto.setLines(List.of());
        return dto;
    }
}
