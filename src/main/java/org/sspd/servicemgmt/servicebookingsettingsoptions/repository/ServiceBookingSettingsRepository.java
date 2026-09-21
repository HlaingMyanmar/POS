package org.sspd.servicemgmt.servicebookingsettingsoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicebookingsettingsoptions.model.ServiceBookingSettings;

public interface ServiceBookingSettingsRepository extends JpaRepository<ServiceBookingSettings, Integer> {
}
