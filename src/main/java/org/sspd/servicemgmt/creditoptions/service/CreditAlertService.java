package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.creditoptions.dto.CreditAlertDTO;
import org.sspd.servicemgmt.creditoptions.mapper.CreditMapper;
import org.sspd.servicemgmt.creditoptions.model.AlertType;
import org.sspd.servicemgmt.creditoptions.model.CreditAlert;
import org.sspd.servicemgmt.creditoptions.repository.CreditAlertRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditAlertService {
    private final CreditAlertRepository repository;
    private final CreditMapper mapper;

    @Transactional
    public void createAlert(AlertType type, Customer customer, Sale sale) {
        if (sale != null && repository.existsBySaleIdAndAlertTypeAndResolvedFalse(sale.getId(), type)) {
            return;
        }
        CreditAlert alert = CreditAlert.builder()
                .alertType(type)
                .customer(customer)
                .sale(sale)
                .alertDate(LocalDateTime.now())
                .resolved(false)
                .build();
        repository.save(alert);
    }

    @Transactional
    public void resolveAlertsForSale(Integer saleId) {
        List<CreditAlert> alerts = repository.findBySaleIdAndResolvedFalse(saleId);
        alerts.forEach(a -> {
            a.setResolved(true);
            a.setResolvedAt(LocalDateTime.now());
        });
        repository.saveAll(alerts);
    }

    @Transactional
    public void evaluateDueAlerts(Sale sale) {
        if (sale.getDueAmount() == null || sale.getDueAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            resolveAlertsForSale(sale.getId());
            return;
        }
        LocalDate dueDate = sale.getDueDate();
        if (dueDate == null) return;
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            createAlert(AlertType.Overdue, sale.getCustomer(), sale);
        } else if (!dueDate.isAfter(today.plusDays(3))) { // use else-if to avoid double alert
            createAlert(AlertType.Due_Soon, sale.getCustomer(), sale);
        }
    }

    @Transactional(readOnly = true)
    public List<CreditAlertDTO> listUnresolved() {
        return repository.findByResolvedFalse().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CreditAlertDTO> listByCustomer(Integer customerId) {
        return repository.findByCustomerIdAndResolvedFalse(customerId).stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @Transactional
    public CreditAlertDTO resolve(Integer alertId) {
        CreditAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(alert));
    }
}
