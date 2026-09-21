package org.sspd.servicemgmt.servicebookingsettingsoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingArrivalWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingArrivalWindowRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.repository.ServiceBookingDateExceptionRepository;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBookingArrivalWindowServiceTest {

    @Mock ServiceBookingArrivalWindowRepository repository;
    @Mock BookingRepository bookingRepository;
    @Mock ServiceBookingDateExceptionRepository dateExceptionRepository;
    private ServiceBookingArrivalWindowService service;

    @BeforeEach
    void setUp() {
        service = new ServiceBookingArrivalWindowService(repository, bookingRepository, dateExceptionRepository);
    }

    @Test
    void rejectsNullOrZeroCapacity() {
        ServiceBookingArrivalWindowDTO dto = validDto();
        dto.setMaxCapacity(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(dto));

        dto.setMaxCapacity(0);
        assertThrows(IllegalArgumentException.class, () -> service.create(dto));
    }

    @Test
    void createsWindowWithExplicitCapacity() {
        when(repository.save(any(ServiceBookingArrivalWindow.class))).thenAnswer(inv -> {
            ServiceBookingArrivalWindow w = inv.getArgument(0);
            w.setId(1);
            return w;
        });
        ServiceBookingArrivalWindowDTO created = service.create(validDto());
        assertEquals(2, created.getMaxCapacity());
        assertEquals("Morning", created.getName());
        verify(repository).save(any(ServiceBookingArrivalWindow.class));
    }

    private ServiceBookingArrivalWindowDTO validDto() {
        ServiceBookingArrivalWindowDTO dto = new ServiceBookingArrivalWindowDTO();
        dto.setName("Morning");
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(12, 0));
        dto.setMaxCapacity(2);
        dto.setActive(true);
        dto.setDisplayOrder(1);
        return dto;
    }
}
