package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;

import java.util.List;
import java.util.Optional;

public interface BookingItemRepository extends JpaRepository<BookingItem, Integer> {
    List<BookingItem> findAllByBookingIdOrderByIdAsc(Integer bookingId);
    Optional<BookingItem> findByIdAndBookingId(Integer id, Integer bookingId);
}
