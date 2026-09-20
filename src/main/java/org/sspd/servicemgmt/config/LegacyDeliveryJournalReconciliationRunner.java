package org.sspd.servicemgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.reportoptions.service.JournalBackfillService;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class LegacyDeliveryJournalReconciliationRunner implements ApplicationRunner {

    private final JournalBackfillService journalBackfillService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int reconciled = journalBackfillService.reconcileLegacyDeliveryIncome();
            if (reconciled > 0) {
                log.info("Reconciled {} legacy delivery income journal(s)", reconciled);
            }
        } catch (Exception e) {
            log.error("Legacy delivery reconciliation failed; application startup will continue", e);
        }
    }
}
