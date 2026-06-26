package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {
    Optional<Booking> findTopByOrderByIdDesc();
    Optional<Booking> findByInvoiceNo(String invoiceNo);
    List<Booking> findByStatus(BookingStatus status);
    List<Booking> findByCustomerId(Integer customerId);

    @Query("SELECT b FROM Booking b WHERE b.appointmentDate BETWEEN :from AND :to AND b.status IN ('Pending','Confirmed')")
    List<Booking> findUpcomingAppointments(LocalDateTime from, LocalDateTime to);

    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.devices WHERE b.id = :id")
    Optional<Booking> findByIdWithDevices(@Param("id") Integer id);

    @Query("""
        SELECT b FROM Booking b
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(b.customer.name) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(b.invoiceNo) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(b.brand) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(b.model) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:dateFrom IS NULL OR b.bookingDate >= :dateFrom)
          AND (:dateTo   IS NULL OR b.bookingDate <  :dateTo)
        """)
    Page<Booking> findBySearchAndDate(@Param("search")   String search,
                                      @Param("dateFrom") LocalDateTime dateFrom,
                                      @Param("dateTo")   LocalDateTime dateTo,
                                      Pageable pageable);
}
