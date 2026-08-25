package org.sspd.servicemgmt.bookingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.bookingoptions.model.BookingDetail;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Integer> {
    boolean existsByServiceItem_Id(Integer serviceItemId);
}
