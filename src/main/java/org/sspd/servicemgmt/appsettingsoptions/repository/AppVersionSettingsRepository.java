package org.sspd.servicemgmt.appsettingsoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.appsettingsoptions.model.AppVersionSettings;

@Repository
public interface AppVersionSettingsRepository extends JpaRepository<AppVersionSettings, Integer> {
}
