package org.sspd.servicemgmt.bookingoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository repository;
    @Mock BookingItemRepository itemRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CompanySettingsRepository companySettingsRepository;
    @Mock ServiceJobRepository serviceJobRepository;
    @Mock ServiceJobService serviceJobService;
    @Mock DataEventPublisher dataEventPublisher;
    @Mock BookingPhotoStorageService bookingPhotoStorageService;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, itemRepository, customerRepository,
            companySettingsRepository, serviceJobRepository, serviceJobService, dataEventPublisher,
            bookingPhotoStorageService);
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
