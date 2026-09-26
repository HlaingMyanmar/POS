package org.sspd.servicemgmt.bookingoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingRequestPhotoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemSummaryProjection;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRequestPhotoRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingSettingsService;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository repository;
    @Mock BookingItemRepository itemRepository;
    @Mock BookingRequestPhotoRepository requestPhotoRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CompanySettingsRepository companySettingsRepository;
    @Mock ServiceJobRepository serviceJobRepository;
    @Mock ServiceJobService serviceJobService;
    @Mock DataEventPublisher dataEventPublisher;
    @Mock BookingPhotoStorageService bookingPhotoStorageService;
    @Mock ServiceBookingSettingsService serviceBookingSettingsService;

    private BookingService service;

    @BeforeEach
    void setUp() {
        lenient().when(serviceBookingSettingsService.getMaxPhotosPerItem()).thenReturn(50);
        service = new BookingService(repository, itemRepository, requestPhotoRepository, customerRepository,
            companySettingsRepository, serviceJobRepository, serviceJobService, dataEventPublisher,
            bookingPhotoStorageService, serviceBookingSettingsService);
    }

    @Test
    void convertsConfirmedBookingToOneOutdoorJob() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(repository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(serviceJobRepository.existsByBookingIdAndServiceMode(10, ServiceMode.OUTDOOR))
                .thenReturn(false, true);
        ServiceJobDTO created = new ServiceJobDTO();
        created.setId(101);
        created.setBookingId(10);
        created.setServiceMode(ServiceMode.OUTDOOR);
        when(serviceJobService.create(any(ServiceJobDTO.class))).thenReturn(created);
        when(serviceJobService.findByBookingId(10)).thenReturn(List.of(created));

        BookingDTO result = service.convertOutdoor(10);

        ArgumentCaptor<ServiceJobDTO> captor = ArgumentCaptor.forClass(ServiceJobDTO.class);
        verify(serviceJobService).create(captor.capture());
        ServiceJobDTO request = captor.getValue();
        assertEquals(10, request.getBookingId());
        assertEquals(7, request.getCustomerId());
        assertEquals(ServiceMode.OUTDOOR, request.getServiceMode());
        assertEquals("Power problem", request.getProblemDesc());
        assertEquals(1, result.getLinkedJobs().size());
        assertTrue(result.isFullyConverted());
    }

    @Test
    void convertsEveryPendingItemToItsOwnIndoorJob() {
        Booking booking = booking(BookingStatus.ARRIVED);
        BookingItem first = item(21, "Printer", "PR-001");
        BookingItem second = item(22, "Monitor", "MN-002");
        first.setBooking(booking);
        second.setBooking(booking);
        booking.getItems().addAll(List.of(first, second));
        when(repository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(serviceJobService.create(any(ServiceJobDTO.class))).thenAnswer(invocation -> {
            ServiceJobDTO request = invocation.getArgument(0);
            ServiceJobDTO created = new ServiceJobDTO();
            created.setId(request.getItemName().equals("Printer") ? 201 : 202);
            created.setBookingId(request.getBookingId());
            created.setServiceMode(request.getServiceMode());
            return created;
        });
        when(serviceJobService.findByBookingId(10)).thenReturn(List.of());

        BookingDTO result = service.convertIndoor(10);

        ArgumentCaptor<ServiceJobDTO> captor = ArgumentCaptor.forClass(ServiceJobDTO.class);
        verify(serviceJobService, org.mockito.Mockito.times(2)).create(captor.capture());
        assertEquals(List.of("Printer", "Monitor"), captor.getAllValues().stream()
                .map(ServiceJobDTO::getItemName).toList());
        assertTrue(captor.getAllValues().stream().allMatch(job -> job.getServiceMode() == ServiceMode.INDOOR));
        assertEquals(201, first.getConvertedJobId());
        assertEquals(202, second.getConvertedJobId());
        assertEquals(0, result.getUnconvertedItemCount());
        assertTrue(result.isFullyConverted());
        verify(itemRepository).saveAll(List.of(first, second));
    }

    @Test
    void refusesCancelAfterAJobHasBeenLinked() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(repository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(serviceJobRepository.findAllByBookingIdOrderByIdAsc(10))
                .thenReturn(List.of(org.mockito.Mockito.mock(
                        org.sspd.servicemgmt.servicejoboptions.model.ServiceJob.class)));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.cancel(10));

        assertTrue(error.getMessage().contains("cannot be canceled"));
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void rejectsConfirmedBookingWithReasonAndAuditActor() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(repository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(serviceJobRepository.findAllByBookingIdOrderByIdAsc(10)).thenReturn(List.of());
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingDTO result = service.reject(10, "  Service area is unavailable  ", "admin@example.com");

        assertEquals(BookingStatus.REJECTED, result.getStatus());
        assertEquals("Service area is unavailable", result.getRejectionReason());
        assertEquals("admin@example.com", result.getRejectedBy());
        assertNotNull(result.getRejectedAt());
    }

    @Test
    void rejectionRequiresReason() {
        Booking booking = booking(BookingStatus.CONFIRMED);
        when(repository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(serviceJobRepository.findAllByBookingIdOrderByIdAsc(10)).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.reject(10, "  ", "admin"));

        assertTrue(error.getMessage().contains("reason is required"));
        verify(repository, never()).save(any(Booking.class));
    }

    @Test
    void createsStructuredCustomerRequestWithStoredPhoto() {
        Customer customer = Customer.builder()
                .id(7).name("Customer").phone("091234567").address("Yangon").build();
        when(customerRepository.findById(7)).thenReturn(Optional.of(customer));
        when(repository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });
        when(repository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companySettingsRepository.findAll()).thenReturn(List.of());
        when(bookingPhotoStorageService.store("data:image/jpeg;base64,AA==", 42, 1))
                .thenReturn(new BookingPhotoStorageService.StoredPhoto(
                        "/uploads/booking-photos/booking-items/42/request.webp",
                        "/uploads/booking-photos/booking-items/42/request-thumb.webp"));

        BookingRequestPhotoDTO photo = new BookingRequestPhotoDTO();
        photo.setSlot(1);
        photo.setFileName("problem.jpg");
        photo.setDataUrl("data:image/jpeg;base64,AA==");
        BookingDTO request = new BookingDTO();
        request.setCustomerId(7);
        request.setComplaintNote("Screen flickers");
        request.setRequestedServiceName("Laptop repair");
        request.setRequestType("DIAGNOSIS");
        request.setDeviceCategory("COMPUTER");
        request.setDeviceName("ThinkPad");
        request.setRequestedServiceMode("ONSITE");
        request.setServiceAddress("Yangon");
        request.setUrgency("SOON");
        request.setContactPreference("VIBER");
        request.setRequestPhotos(List.of(photo));

        BookingDTO created = service.create(request);

        assertEquals("BK-000042", created.getBookingNo());
        assertEquals("Laptop repair", created.getRequestedServiceName());
        assertEquals("ONSITE", created.getRequestedServiceMode());
        assertEquals("Yangon", created.getServiceAddress());
        assertEquals(1, created.getRequestPhotos().size());
        assertEquals("/uploads/booking-photos/booking-items/42/request.webp",
                created.getRequestPhotos().get(0).getImagePath());
        verify(bookingPhotoStorageService).store("data:image/jpeg;base64,AA==", 42, 1);
    }

    @Test
    void findAllUsesBatchItemStatsAndSkipsRequestPhotoLazyLoads() {
        Booking first = booking(BookingStatus.CONFIRMED);
        Booking second = booking(BookingStatus.ARRIVED);
        second.setId(11);
        second.setBookingNo("BK-000011");
        when(repository.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(first, second)));
        BookingItemSummaryProjection arrivedStats = org.mockito.Mockito.mock(BookingItemSummaryProjection.class);
        when(arrivedStats.getBookingId()).thenReturn(11);
        when(arrivedStats.getItemCount()).thenReturn(2L);
        when(arrivedStats.getUnconvertedCount()).thenReturn(1L);
        when(itemRepository.summarizeByBookingIds(any())).thenReturn(List.of(arrivedStats));
        when(serviceJobRepository.findBookingIdsByServiceMode(any(), eq(ServiceMode.OUTDOOR)))
                .thenReturn(List.of(10));

        var page = service.findAll(null, null, null, null, null, null, 0, 20);

        assertEquals(2, page.getContent().size());
        assertTrue(page.getContent().get(0).isFullyConverted());
        assertEquals(0, page.getContent().get(0).getUnconvertedItemCount());
        assertTrue(page.getContent().get(0).getRequestPhotos().isEmpty());
        assertEquals(1, page.getContent().get(1).getUnconvertedItemCount());
        assertTrue(!page.getContent().get(1).isFullyConverted());
        verify(requestPhotoRepository, never()).findAllByBookingIdIn(any());
        verify(itemRepository).summarizeByBookingIds(any());
        verify(serviceJobRepository).findBookingIdsByServiceMode(any(), eq(ServiceMode.OUTDOOR));
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder()
                .id(10)
                .bookingNo("BK-000010")
                .customer(Customer.builder().id(7).name("Customer").phone("091234567").address("Yangon").build())
                .bookingDate(LocalDate.of(2026, 9, 1))
                .complaintNote("Power problem")
                .status(status)
                .items(new ArrayList<>())
                .build();
    }

    private BookingItem item(int id, String name, String serialNo) {
        return BookingItem.builder()
                .id(id)
                .itemName(name)
                .deviceType("Device")
                .serialNo(serialNo)
                .problemDesc("Does not start")
                .build();
    }
}
