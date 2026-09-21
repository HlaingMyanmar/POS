package org.sspd.servicemgmt.servicebookingsettingsoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingDateException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceBookingDateExceptionRepository extends JpaRepository<ServiceBookingDateException, Integer> {

    List<ServiceBookingDateException> findAllByOrderByExceptionDateAsc();

    Optional<ServiceBookingDateException> findByExceptionDate(LocalDate exceptionDate);

    List<ServiceBookingDateException> findByExceptionDateBetweenOrderByExceptionDateAsc(LocalDate from, LocalDate to);

    boolean existsByExceptionDateAndIdNot(LocalDate exceptionDate, Integer id);
}
