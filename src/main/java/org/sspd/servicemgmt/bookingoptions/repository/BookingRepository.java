package org.sspd.servicemgmt.bookingoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.bookingoptions.model.Booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
    @EntityGraph(attributePaths = "customer")
    @Query("""
        select b from Booking b
        where (:search is null or :search = ''
            or lower(b.bookingNo) like lower(concat('%', :search, '%'))
            or lower(b.customer.name) like lower(concat('%', :search, '%'))
            or lower(b.customer.phone) like lower(concat('%', :search, '%'))
            or lower(coalesce(b.complaintNote, '')) like lower(concat('%', :search, '%')))
          and (:dateFrom is null or b.bookingDate >= :dateFrom)
          and (:dateTo is null or b.bookingDate <= :dateTo)
        """)
    Page<Booking> search(
            @Param("search") String search,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Integer id);

    @Query("""
        select distinct b from Booking b
        left join fetch b.customer
        left join fetch b.items
        where b.id = :id
        """)
    Optional<Booking> findByIdWithItems(@Param("id") Integer id);

    @EntityGraph(attributePaths = "customer")
    List<Booking> findByCustomer_IdOrderByIdDesc(Integer customerId);

    long countByArrivalWindowId(Integer arrivalWindowId);

    /** Outdoor capacity consumers: CONFIRMED only (ARRIVED is shop intake). */
    long countByServiceDateAndArrivalWindowIdAndStatus(
            LocalDate serviceDate, Integer arrivalWindowId, org.sspd.servicemgmt.bookingoptions.model.BookingStatus status);
}
