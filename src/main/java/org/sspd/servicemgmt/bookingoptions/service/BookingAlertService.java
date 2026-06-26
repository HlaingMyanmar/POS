package org.sspd.servicemgmt.bookingoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAlertService {

    private final BookingService bookingService;
    private final SimpMessagingTemplate messagingTemplate;

    // Check every 5 minutes for appointments within next 30 minutes
    @Scheduled(fixedRate = 300_000)
    void checkUpcomingAppointments() {
        try {
            List<BookingDTO> upcoming = bookingService.findUpcoming(30);
            if (!upcoming.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/booking-alerts", upcoming);
                log.info("Sent {} booking alert(s)", upcoming.size());
            }
        } catch (Exception e) {
            log.error("Booking alert check error", e);
        }
    }
}
