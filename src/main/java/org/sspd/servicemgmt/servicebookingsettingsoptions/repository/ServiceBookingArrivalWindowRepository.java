package org.sspd.servicemgmt.servicebookingsettingsoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingArrivalWindow;

import java.util.List;
import java.util.Optional;

public interface ServiceBookingArrivalWindowRepository extends JpaRepository<ServiceBookingArrivalWindow, Integer> {

    List<ServiceBookingArrivalWindow> findAllByOrderByDisplayOrderAscIdAsc();

    List<ServiceBookingArrivalWindow> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ServiceBookingArrivalWindow w where w.id = :id")
    Optional<ServiceBookingArrivalWindow> findByIdForUpdate(@Param("id") Integer id);
}
