package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.creditoptions.model.AlertType;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTerm;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditTermRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CustomerCreditTermRepository termRepository;
    private final SaleRepository saleRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final CreditAlertService alertService;

    @Transactional(readOnly = true)
    public LocalDate resolveDueDate(Integer customerId, LocalDateTime saleDate, LocalDate requested, BigDecimal dueAmount) {
        if (dueAmount == null || dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        CustomerCreditTerm term = termRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new RuntimeException("Credit terms not set for customer"));
        if (!Boolean.TRUE.equals(term.getCreditAllowed())) {
            throw new RuntimeException("Customer is not allowed credit");
        }
        if (requested != null) {
            if (requested.isBefore(LocalDate.now())) {
                throw new RuntimeException("Due date cannot be in the past");
            }
            return requested;
        }
        LocalDate base = saleDate != null ? saleDate.toLocalDate() : LocalDate.now();
        Integer days = term.getCreditDays() != null ? term.getCreditDays() : 0;
        return base.plusDays(days);
    }

    @Transactional
    public void enforceCreditLimit(Integer customerId, BigDecimal newDue, Sale sale) {
        if (newDue == null || newDue.compareTo(BigDecimal.ZERO) <= 0) return;

        CustomerCreditTerm term = termRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new RuntimeException("Credit terms not set for customer"));

        if (!Boolean.TRUE.equals(term.getCreditAllowed())) {
            throw new RuntimeException("Customer is not allowed credit");
        }

        BigDecimal limit = term.getCreditLimit() != null ? term.getCreditLimit() : BigDecimal.ZERO;

        BigDecimal saleOutstanding = saleRepository.sumOutstandingDue(customerId, sale != null ? sale.getId() : null);
        BigDecimal jobOutstanding = serviceJobRepository.sumOutstandingDue(customerId, null);
        BigDecimal outstanding = safe(saleOutstanding).add(safe(jobOutstanding));
        BigDecimal projected = outstanding.add(newDue);
        if (limit.compareTo(BigDecimal.ZERO) > 0 && projected.compareTo(limit) > 0) {
            if (sale != null) {
                alertService.createAlert(AlertType.Credit_Limit_Exceeded, sale.getCustomer(), sale);
            }
            throw new RuntimeException("Credit limit exceeded for customer");
        }
        if (limit.compareTo(BigDecimal.ZERO) == 0 && projected.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Credit limit exceeded for customer");
        }
    }

    @Transactional
    public void enforceCreditLimitForServiceJob(Integer customerId, BigDecimal newDue, Customer customer, Integer excludeJobId) {
        if (newDue == null || newDue.compareTo(BigDecimal.ZERO) <= 0) return;

        CustomerCreditTerm term = termRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new RuntimeException("Credit terms not set for customer"));

        if (!Boolean.TRUE.equals(term.getCreditAllowed())) {
            throw new RuntimeException("Customer is not allowed credit");
        }

        BigDecimal limit = term.getCreditLimit() != null ? term.getCreditLimit() : BigDecimal.ZERO;

        BigDecimal saleOutstanding = saleRepository.sumOutstandingDue(customerId, null);
        BigDecimal jobOutstanding = serviceJobRepository.sumOutstandingDue(customerId, excludeJobId);
        BigDecimal outstanding = safe(saleOutstanding).add(safe(jobOutstanding));
        BigDecimal projected = outstanding.add(newDue);
        if (limit.compareTo(BigDecimal.ZERO) > 0 && projected.compareTo(limit) > 0) {
            alertService.createAlert(AlertType.Credit_Limit_Exceeded, customer, null);
            throw new RuntimeException("Credit limit exceeded for customer");
        }
        if (limit.compareTo(BigDecimal.ZERO) == 0 && projected.compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Credit limit exceeded for customer");
        }
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
