package org.sspd.servicemgmt.creditoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.creditoptions.model.CreditOverrideLog;

@Repository
public interface CreditOverrideLogRepository extends JpaRepository<CreditOverrideLog, Integer> {
}
