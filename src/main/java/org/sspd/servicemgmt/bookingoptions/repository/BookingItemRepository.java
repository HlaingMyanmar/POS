package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingItemRepository extends JpaRepository<BookingItem, Integer> {
    List<BookingItem> findAllByBookingIdOrderByIdAsc(Integer bookingId);
    Optional<BookingItem> findByIdAndBookingId(Integer id, Integer bookingId);

    @Query("""
        select i.booking.id as bookingId,
               count(i) as itemCount,
               coalesce(sum(case when i.convertedJobId is null then 1L else 0L end), 0L) as unconvertedCount
        from BookingItem i
        where i.booking.id in :bookingIds
        group by i.booking.id
        """)
    List<BookingItemSummaryProjection> summarizeByBookingIds(@Param("bookingIds") Collection<Integer> bookingIds);
}
