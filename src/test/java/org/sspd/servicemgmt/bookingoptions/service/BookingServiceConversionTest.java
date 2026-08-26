package org.sspd.servicemgmt.bookingoptions.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingAttachment;
import org.sspd.servicemgmt.bookingoptions.model.BookingDevice;
import org.sspd.servicemgmt.bookingoptions.model.BookingDetail;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingAttachmentRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobAttachment;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobAttachmentRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;
import org.sspd.servicemgmt.shelflocationoptions.repository.ShelfLocationRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceConversionTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingAttachmentRepository attachmentRepository;
    @Mock CustomerRepository customerRepository;
    @Mock StaffRepository staffRepository;
    @Mock ServiceJobRepository serviceJobRepository;
    @Mock ServiceJobAttachmentRepository jobAttachmentRepository;
    @Mock ShelfLocationRepository shelfLocationRepository;
    @Mock CompanySettingsService companySettingsService;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks BookingService service;

    @Test
    void convertsEachDeviceAndPreservesLinkedData() {
        Customer customer = new Customer();
        customer.setId(7);
        customer.setName("Customer");

        BookingDevice phone = BookingDevice.builder()
            .brand("Apple").model("iPhone 15").deviceType("Phone")
            .serialNumber("SN-PHONE").color("Black").accessories("Cable")
            .problemDesc("No power").deviceConditions("Screen scratched")
            .partRequests("[{\"partName\":\"Battery\",\"action\":\"REPLACE\",\"qty\":1}]")
            .build();
        BookingDevice laptop = BookingDevice.builder()
            .brand("Lenovo").model("T14").deviceType("Laptop")
            .serialNumber("SN-LAPTOP").color("Silver").accessories("Charger")
            .problemDesc("No display").deviceConditions("Body dent")
            .partRequests("[{\"partName\":\"LCD\",\"action\":\"CHECK\",\"qty\":1}]")
            .build();

        ServiceItem batteryService = ServiceItem.builder()
            .id(11).code("SVC-BAT").item("Battery Service").price(new BigDecimal("10000")).build();
        ServiceItem displayService = ServiceItem.builder()
            .id(12).code("SVC-DIS").item("Display Service").price(new BigDecimal("15000")).build();
        BookingDetail phoneDetail = BookingDetail.builder()
            .serviceItem(batteryService).deviceIndex(0).qty(1)
            .price(new BigDecimal("10000")).subtotal(new BigDecimal("10000")).build();
        BookingDetail laptopDetail = BookingDetail.builder()
            .serviceItem(displayService).deviceIndex(1).qty(1)
            .price(new BigDecimal("15000")).subtotal(new BigDecimal("15000")).build();

        Booking booking = Booking.builder()
            .id(42).invoiceNo("BK-000042").customer(customer)
            .status(BookingStatus.Pending).totalAmount(new BigDecimal("25000"))
            .shelfLocation("A-01").devices(new ArrayList<>(List.of(phone, laptop)))
            .details(new ArrayList<>(List.of(phoneDetail, laptopDetail)))
            .deviceInfos(new ArrayList<>()).build();
        ShelfLocation shelf = ShelfLocation.builder().id(3).code("A-01").label("Front").active(true).build();

        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(attachmentRepository.findByBookingIdOrderByUploadedAtDesc(42)).thenReturn(List.of());
        when(shelfLocationRepository.findByCodeIgnoreCase("A-01")).thenReturn(Optional.of(shelf));
        AtomicInteger ids = new AtomicInteger();
        when(serviceJobRepository.findTopByOrderByIdDesc()).thenAnswer(invocation -> {
            int id = ids.get();
            return id == 0 ? Optional.empty() : Optional.of(ServiceJob.builder().id(id).build());
        });
        when(serviceJobRepository.save(any(ServiceJob.class))).thenAnswer(invocation -> {
            ServiceJob job = invocation.getArgument(0);
            job.setId(ids.incrementAndGet());
            job.setReceivedDate(LocalDateTime.now());
            return job;
        });
        when(bookingRepository.save(booking)).thenReturn(booking);

        List<ServiceJobDTO> result = service.convertToJob(42);

        assertEquals(2, result.size());
        assertEquals(BookingStatus.Converted, booking.getStatus());
        assertAll(
            () -> assertEquals("SN-PHONE", result.get(0).getSerialNo()),
            () -> assertEquals("Black", result.get(0).getColor()),
            () -> assertEquals("Cable", result.get(0).getAccessories()),
            () -> assertEquals("Screen scratched", result.get(0).getDeviceConditions()),
            () -> assertEquals("[{\"partName\":\"Battery\",\"action\":\"REPLACE\",\"qty\":1}]", result.get(0).getPartRequests()),
            () -> assertEquals(1, result.get(0).getLines().size()),
            () -> assertEquals("Battery Service", result.get(0).getLines().get(0).getServiceItemName()),
            () -> assertEquals(0, new BigDecimal("10000").compareTo(result.get(0).getEstimatedCost())),
            () -> assertEquals("SN-LAPTOP", result.get(1).getSerialNo()),
            () -> assertEquals("Charger", result.get(1).getAccessories()),
            () -> assertEquals("[{\"partName\":\"LCD\",\"action\":\"CHECK\",\"qty\":1}]", result.get(1).getPartRequests()),
            () -> assertEquals(1, result.get(1).getLines().size()),
            () -> assertEquals("Display Service", result.get(1).getLines().get(0).getServiceItemName()),
            () -> assertEquals(0, new BigDecimal("15000").compareTo(result.get(1).getEstimatedCost())),
            () -> assertEquals(42, result.get(1).getBookingId()),
            () -> assertEquals("BK-000042", result.get(1).getBookingNo())
        );
        verify(serviceJobRepository, times(2)).save(argThat(job -> job.getShelfLocation() == shelf));
        verify(messagingTemplate).convertAndSend("/topic/booking", "BOOKING_UPDATED");
        verify(messagingTemplate).convertAndSend("/topic/service-jobs", "JOB_CREATED_FROM_BOOKING");
        verifyNoInteractions(jobAttachmentRepository);
    }

    @Test
    void formatsChecklistAndCopiesMatchingPhotos() {
        Customer customer = new Customer();
        customer.setId(7);
        customer.setName("Customer");

        BookingDevice phone = BookingDevice.builder()
            .brand("Apple").model("iPhone 15").deviceType("Phone")
            .conditionChecklist("[{\"name\":\"Screen\",\"status\":\"Damaged\",\"notice\":\"crack\"},{\"name\":\"Body\",\"status\":\"Good\",\"notice\":\"\"}]")
            .deviceConditions("Corner scratch")
            .build();
        BookingDevice laptop = BookingDevice.builder()
            .brand("Lenovo").model("T14").deviceType("Laptop")
            .build();
        Booking booking = Booking.builder()
            .id(42).invoiceNo("BK-000042").customer(customer)
            .status(BookingStatus.Pending)
            .devices(new ArrayList<>(List.of(phone, laptop)))
            .deviceInfos(new ArrayList<>()).build();
        BookingAttachment phonePhoto = BookingAttachment.builder()
            .attachmentType("INTAKE_PHOTO_DEVICE_1").fileName("phone.jpg")
            .contentType("image/jpeg").dataUrl("data:image/jpeg;base64,abc").build();
        BookingAttachment laptopPhoto = BookingAttachment.builder()
            .attachmentType("INTAKE_PHOTO_DEVICE_2").fileName("laptop.jpg")
            .contentType("image/jpeg").dataUrl("data:image/jpeg;base64,def").build();

        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(attachmentRepository.findByBookingIdOrderByUploadedAtDesc(42))
            .thenReturn(List.of(phonePhoto, laptopPhoto));
        AtomicInteger ids = new AtomicInteger();
        when(serviceJobRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(serviceJobRepository.save(any(ServiceJob.class))).thenAnswer(invocation -> {
            ServiceJob job = invocation.getArgument(0);
            job.setId(ids.incrementAndGet());
            job.setReceivedDate(LocalDateTime.now());
            return job;
        });
        when(jobAttachmentRepository.save(any(ServiceJobAttachment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.save(booking)).thenReturn(booking);

        List<ServiceJobDTO> result = service.convertToJob(42);

        assertEquals("Screen - Damaged (crack) | Body - Good", result.get(0).getItemCondition());
        assertEquals("Corner scratch", result.get(0).getDeviceConditions());
        verify(jobAttachmentRepository).save(argThat(photo ->
            photo.getServiceJob().getId() == 1 && "phone.jpg".equals(photo.getFileName())));
        verify(jobAttachmentRepository).save(argThat(photo ->
            photo.getServiceJob().getId() == 2 && "laptop.jpg".equals(photo.getFileName())));
    }

    @Test
    void rejectsAnAlreadyConvertedBookingBeforeCreatingJobs() {
        Booking booking = Booking.builder().id(42).status(BookingStatus.Converted).build();
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class, () -> service.convertToJob(42));

        verifyNoInteractions(serviceJobRepository, shelfLocationRepository);
    }
}