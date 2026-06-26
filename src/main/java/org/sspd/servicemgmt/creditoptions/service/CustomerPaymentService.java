package org.sspd.servicemgmt.creditoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.mapper.CreditMapper;
import org.sspd.servicemgmt.creditoptions.model.CustomerPayment;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CustomerPaymentRepository repository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final StaffRepository staffRepository;
    private final CreditMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @Transactional
    public CustomerPaymentDTO createAdvancePayment(CustomerPaymentDTO dto) {
        if (dto.getSaleId() != null) {
            throw new RuntimeException("Use sale payment API for settling invoices");
        }
        CustomerPayment payment = toEntity(dto, null);
        return mapper.toDto(repository.save(payment));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<CustomerPaymentDTO> findByCustomer(Integer customerId) {
        return repository.findByCustomerId(customerId).stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<CustomerPaymentDTO> findBySale(Integer saleId) {
        return repository.findBySaleId(saleId).stream().map(mapper::toDto).toList();
    }

    /**
     * Used internally by SaleService to persist invoice-linked payments.
     */
    @Transactional
    public void recordSalePayment(Sale sale, CustomerPaymentDTO dto) {
        CustomerPayment payment = toEntity(dto, sale);
        repository.save(payment);
    }

    private CustomerPayment toEntity(CustomerPaymentDTO dto, Sale sale) {
        Customer customer = customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));

        Staff staff = null;
        if (dto.getStaffId() != null) {
            staff = staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        } else if (sale != null && sale.getStaff() != null) {
            staff = sale.getStaff();
        } else {
            throw new RuntimeException("Staff is required for payment");
        }

        CustomerPayment payment = mapper.toEntity(dto);
        payment.setCustomer(customer);
        payment.setSale(sale);
        payment.setPaymentMethod(method);
        payment.setStaff(staff);
        payment.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : LocalDateTime.now());
        return payment;
    }
}
