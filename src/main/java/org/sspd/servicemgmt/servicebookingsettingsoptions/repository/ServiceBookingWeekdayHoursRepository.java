package org.sspd.servicemgmt.servicebookingsettingsoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingWeekdayHours;

import java.util.List;

public interface ServiceBookingWeekdayHoursRepository extends JpaRepository<ServiceBookingWeekdayHours, Integer> {

    List<ServiceBookingWeekdayHours> findAllByOrderByDayOfWeekAscDisplayOrderAscIdAsc();

    List<ServiceBookingWeekdayHours> findByDayOfWeekIgnoreCaseAndActiveTrueOrderByDisplayOrderAscIdAsc(String dayOfWeek);
}
