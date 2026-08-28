package org.sspd.servicemgmt.technicianvisitoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitReportDTO;
import org.sspd.servicemgmt.technicianvisitoptions.controller.TechnicianVisitController;
import org.sspd.servicemgmt.technicianvisitoptions.dto.DeleteGpsHistoryRequest;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianLocationPing;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisit;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEvent;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEventType;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitStatus;
import org.sspd.servicemgmt.technicianvisitoptions.repository.StaffLiveLocationRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianLocationPingRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianVisitEventRepository;
import org.sspd.servicemgmt.technicianvisitoptions.repository.TechnicianVisitRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TechnicianVisitServiceReportTest {

    @Test
    void calculatesDurationsDistanceStopsArrivalAndGpsGap() {
        TechnicianVisitRepository visits = mock(TechnicianVisitRepository.class);
        StaffLiveLocationRepository live = mock(StaffLiveLocationRepository.class);
        TechnicianLocationPingRepository pings = mock(TechnicianLocationPingRepository.class);
        TechnicianVisitEventRepository events = mock(TechnicianVisitEventRepository.class);
        TechnicianVisitService service = new TechnicianVisitService(
                visits,
                live,
                pings,
                events,
                mock(ServiceJobRepository.class),
                mock(StaffRepository.class),
                mock(UserRepository.class),
                mock(SimpMessagingTemplate.class)
        );

        LocalDateTime start = LocalDateTime.of(2026, 8, 28, 8, 0);
        Customer customer = new Customer();
        customer.setId(11);
        customer.setName("Acme Customer");
        customer.setLatitude(new BigDecimal("16.8410000"));
        customer.setLongitude(new BigDecimal("96.1735000"));
        Staff staff = new Staff();
        staff.setId(7);
        staff.setName("Technician One");
        ServiceJob job = new ServiceJob();
        job.setId(21);
        job.setJobNo("SJ-000021");
        job.setCustomer(customer);
        TechnicianVisit visit = TechnicianVisit.builder()
                .id(31L)
                .staff(staff)
                .serviceJob(job)
                .customer(customer)
                .status(TechnicianVisitStatus.COMPLETED)
                .startedAt(start)
                .arrivedAt(start.plusMinutes(30))
                .leftCustomerAt(start.plusMinutes(90))
                .endedAt(start.plusMinutes(120))
                .arriveLatitude(new BigDecimal("16.8410500"))
                .arriveLongitude(new BigDecimal("96.1735000"))
                .build();
        List<TechnicianLocationPing> tripPings = List.of(
                ping(visit, "16.8300000", "96.1600000", start),
                ping(visit, "16.8350000", "96.1660000", start.plusMinutes(2)),
                ping(visit, "16.8410500", "96.1735000", start.plusMinutes(10))
        );
        List<TechnicianVisitEvent> timeline = List.of(
                event(visit, TechnicianVisitEventType.STOPPED, start.plusMinutes(10), null, null),
                event(visit, TechnicianVisitEventType.REASON_ADDED, start.plusMinutes(12), "TRAFFIC", "Road blocked"),
                event(visit, TechnicianVisitEventType.RESUMED, start.plusMinutes(20), null, null)
        );
        when(visits.findByStartedAtGreaterThanEqualAndStartedAtLessThanEqualOrderByStartedAtDesc(any(), any()))
                .thenReturn(List.of(visit));
        when(pings.findByVisit_IdOrderByRecordedAtAscIdAsc(31L)).thenReturn(tripPings);
        when(events.findByVisitIdOrderByOccurredAtAscIdAsc(31L)).thenReturn(timeline);

        List<TechnicianVisitReportDTO> result = service.report(
                start.minusHours(1),
                start.plusDays(1),
                "000021",
                "acme"
        );

        assertEquals(1, result.size());
        TechnicianVisitReportDTO row = result.get(0);
        assertEquals(30L, row.outboundMinutes());
        assertEquals(60L, row.onSiteMinutes());
        assertEquals(30L, row.returnMinutes());
        assertEquals(120L, row.totalMinutes());
        assertEquals(1, row.stopCount());
        assertEquals(10L, row.stopMinutes());
        assertEquals(List.of("TRAFFIC: Road blocked"), row.stopReasons());
        assertEquals(3, row.gpsPointCount());
        assertEquals(8L, row.maxGpsGapMinutes());
        assertTrue(row.actualDistanceMeters() > 0);
        assertTrue(row.arrivalVerified());
        assertTrue(row.gpsException().contains("GPS_GAP"));
    }

    @Test
    void rejectsInvalidDateRange() {
        TechnicianVisitService service = new TechnicianVisitService(
                mock(TechnicianVisitRepository.class),
                mock(StaffLiveLocationRepository.class),
                mock(TechnicianLocationPingRepository.class),
                mock(TechnicianVisitEventRepository.class),
                mock(ServiceJobRepository.class),
                mock(StaffRepository.class),
                mock(UserRepository.class),
                mock(SimpMessagingTemplate.class)
        );
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> service.report(now, now.minusDays(1), null, null));
    }

    @Test
    void rawGpsDeletionRequiresDedicatedDeletePermission() throws Exception {
        PreAuthorize guard = TechnicianVisitController.class
                .getMethod(
                        "deleteHistoryPings",
                        Long.class,
                        DeleteGpsHistoryRequest.class,
                        org.springframework.security.core.Authentication.class
                )
                .getAnnotation(PreAuthorize.class);
        assertNotNull(guard);
        assertEquals(
                "hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_DELETE')",
                guard.value()
        );
    }

    @Test
    void rawGpsDeletionRequiresExactConfirmationAndCreatesAuditEvent() {
        TechnicianVisitRepository visits = mock(TechnicianVisitRepository.class);
        TechnicianLocationPingRepository pings = mock(TechnicianLocationPingRepository.class);
        TechnicianVisitEventRepository events = mock(TechnicianVisitEventRepository.class);
        TechnicianVisitService service = new TechnicianVisitService(
                visits,
                mock(StaffLiveLocationRepository.class),
                pings,
                events,
                mock(ServiceJobRepository.class),
                mock(StaffRepository.class),
                mock(UserRepository.class),
                mock(SimpMessagingTemplate.class)
        );
        Customer customer = new Customer();
        customer.setId(1);
        customer.setName("Customer");
        Staff staff = new Staff();
        staff.setId(2);
        staff.setName("Technician");
        ServiceJob job = new ServiceJob();
        job.setId(3);
        job.setJobNo("SJ-3");
        job.setCustomer(customer);
        TechnicianVisit visit = TechnicianVisit.builder()
                .id(44L)
                .staff(staff)
                .serviceJob(job)
                .customer(customer)
                .status(TechnicianVisitStatus.COMPLETED)
                .build();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        when(visits.findById(44L)).thenReturn(Optional.of(visit));
        when(pings.deleteByVisit_Id(44L)).thenReturn(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteVisitGpsHistory(44L, "DELETE", "privacy request", authentication)
        );
        int deleted = service.deleteVisitGpsHistory(
                44L,
                "DELETE GPS 44",
                "privacy request",
                authentication
        );

        assertEquals(3, deleted);
        verify(pings).deleteByVisit_Id(44L);
        verify(events).save(argThat(event ->
                event.getEventType() == TechnicianVisitEventType.GPS_HISTORY_DELETED
                        && event.getNote().contains("admin")
                        && event.getNote().contains("privacy request")
        ));
    }

    private static TechnicianLocationPing ping(
            TechnicianVisit visit,
            String latitude,
            String longitude,
            LocalDateTime recordedAt
    ) {
        return TechnicianLocationPing.builder()
                .visit(visit)
                .clientPingId(java.util.UUID.randomUUID().toString())
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .accuracy(BigDecimal.TEN)
                .recordedAt(recordedAt)
                .receivedAt(recordedAt)
                .build();
    }

    private static TechnicianVisitEvent event(
            TechnicianVisit visit,
            TechnicianVisitEventType type,
            LocalDateTime occurredAt,
            String reason,
            String note
    ) {
        return TechnicianVisitEvent.builder()
                .visit(visit)
                .eventType(type)
                .occurredAt(occurredAt)
                .reasonCode(reason)
                .note(note)
                .build();
    }
}
