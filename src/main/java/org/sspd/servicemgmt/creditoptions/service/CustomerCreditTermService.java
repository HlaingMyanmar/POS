package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditTermDTO;
import org.sspd.servicemgmt.creditoptions.mapper.CreditMapper;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTermHistory;
import org.sspd.servicemgmt.creditoptions.model.CustomerCreditTerm;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditTermRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerCreditTermService {

    private final CustomerCreditTermRepository repository;
    private final CustomerRepository customerRepository;
    private final CreditMapper mapper;
    private final org.sspd.servicemgmt.creditoptions.repository.CustomerCreditTermHistoryRepository historyRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<CustomerCreditTermDTO> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public CustomerCreditTermDTO findByCustomer(Integer customerId) {
        CustomerCreditTerm term = repository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit term not found for customer " + customerId));
        return mapper.toDto(term);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @Transactional
    public CustomerCreditTermDTO saveOrUpdate(CustomerCreditTermDTO dto) {
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        CustomerCreditTerm existing = repository.findByCustomerId(dto.getCustomerId()).orElse(null);
        CustomerCreditTerm term = existing != null ? existing : new CustomerCreditTerm();
        term.setCustomer(customer);
        term.setCreditAllowed(Boolean.TRUE.equals(dto.getCreditAllowed()));
        term.setCreditDays(dto.getCreditDays() != null ? dto.getCreditDays() : 0);
        term.setCreditLimit(dto.getCreditLimit() != null ? dto.getCreditLimit() : java.math.BigDecimal.ZERO);

        CustomerCreditTerm saved = repository.save(term);
        maybeLogHistory(existing, saved);
        return mapper.toDto(saved);
    }

    private void maybeLogHistory(CustomerCreditTerm oldTerm, CustomerCreditTerm newTerm) {
        if (oldTerm == null) {
            // first time setup, log creation
            historyRepository.save(CustomerCreditTermHistory.builder()
                    .customer(newTerm.getCustomer())
                    .oldCreditAllowed(null)
                    .newCreditAllowed(newTerm.getCreditAllowed())
                    .oldCreditDays(null)
                    .newCreditDays(newTerm.getCreditDays())
                    .oldCreditLimit(null)
                    .newCreditLimit(newTerm.getCreditLimit())
                    .build());
            return;
        }
        boolean changed = false;
        CustomerCreditTermHistory.CustomerCreditTermHistoryBuilder builder = CustomerCreditTermHistory.builder()
                .customer(newTerm.getCustomer())
                .oldCreditAllowed(oldTerm.getCreditAllowed())
                .newCreditAllowed(newTerm.getCreditAllowed())
                .oldCreditDays(oldTerm.getCreditDays())
                .newCreditDays(newTerm.getCreditDays())
                .oldCreditLimit(oldTerm.getCreditLimit())
                .newCreditLimit(newTerm.getCreditLimit());

        if (!java.util.Objects.equals(oldTerm.getCreditAllowed(), newTerm.getCreditAllowed())) changed = true;
        if (!java.util.Objects.equals(oldTerm.getCreditDays(), newTerm.getCreditDays())) changed = true;
        if (oldTerm.getCreditLimit() == null ? newTerm.getCreditLimit() != null : oldTerm.getCreditLimit().compareTo(newTerm.getCreditLimit()) != 0) changed = true;

        if (changed) {
            historyRepository.save(builder.build());
        }
    }
}
