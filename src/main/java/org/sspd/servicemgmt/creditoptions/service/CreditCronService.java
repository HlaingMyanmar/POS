package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreditCronService {

    private final SaleRepository saleRepository;
    private final CreditAlertService creditAlertService;

    // Run every night 02:00 server time
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Rangoon")
    @Transactional
    public void checkOverdues() {
        List<Sale> openSales = saleRepository.findByCreditStatusInAndDueAmountGreaterThan(
                List.of(CreditStatus.Active, CreditStatus.Overdue),
                BigDecimal.ZERO);
        openSales.forEach(creditAlertService::evaluateDueAlerts);
    }
}
