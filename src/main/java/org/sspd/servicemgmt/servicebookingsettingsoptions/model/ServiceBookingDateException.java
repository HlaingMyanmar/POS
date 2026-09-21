package org.sspd.servicemgmt.servicebookingsettingsoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "service_booking_date_exceptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBookingDateException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "exception_date", nullable = false, unique = true)
    private LocalDate exceptionDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean closed = false;

    /** When not closed, optional short-day override (single continuous span). */
    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    @Column(length = 500)
    private String reason;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "service_booking_date_exception_windows",
            joinColumns = @JoinColumn(name = "exception_id")
    )
    @Column(name = "arrival_window_id")
    private Set<Integer> disabledArrivalWindowIds = new HashSet<>();
}
