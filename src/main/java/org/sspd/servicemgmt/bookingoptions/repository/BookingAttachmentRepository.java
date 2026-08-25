package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.bookingoptions.model.BookingAttachment;

import java.util.List;

public interface BookingAttachmentRepository extends JpaRepository<BookingAttachment, Integer> {
    List<BookingAttachment> findByBookingIdOrderByUploadedAtDesc(Integer bookingId);
}
