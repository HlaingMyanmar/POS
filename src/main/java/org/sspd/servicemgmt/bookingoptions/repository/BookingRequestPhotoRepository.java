package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.bookingoptions.model.BookingRequestPhoto;

import java.util.Collection;
import java.util.List;

public interface BookingRequestPhotoRepository extends JpaRepository<BookingRequestPhoto, Integer> {

    @Query("""
        select p from BookingRequestPhoto p
        where p.booking.id in :bookingIds
        order by p.booking.id asc, p.slot asc
        """)
    List<BookingRequestPhoto> findAllByBookingIdIn(@Param("bookingIds") Collection<Integer> bookingIds);
}
