package org.sspd.servicemgmt.customerportaloptions.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountLinkRequest;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerAppAccountRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAppAccountAdminServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock CustomerAppAccountRepository accountRepository;
    @Mock CustomerOrderRepository orderRepository;
    @Mock CustomerAppActivityService activityService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks CustomerAppAccountAdminService service;

    @Test
    void createForCustomer_rejectsExistingAccount() {
        Customer customer = Customer.builder().id(12).name("A").phone("0911111111").address("Yangon").build();
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomer_Id(12)).thenReturn(Optional.of(new CustomerAppAccount()));

        CustomerAppAccountLinkRequest req = new CustomerAppAccountLinkRequest();
        req.setCustomerId(12);
        assertThrows(IllegalArgumentException.class, () -> service.createForCustomer(req));
    }

    @Test
    void createForCustomer_generatesPassword() {
        Customer customer = Customer.builder().id(12).name("A").phone("0911111111").address("Yangon").build();
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(accountRepository.findByCustomer_Id(12)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(accountRepository.save(any())).thenAnswer(inv -> {
            CustomerAppAccount account = inv.getArgument(0);
            account.setId(9);
            return account;
        });

        CustomerAppAccountLinkRequest req = new CustomerAppAccountLinkRequest();
        req.setCustomerId(12);
        req.setGeneratePassword(true);
        var result = service.createForCustomer(req);
        assertNotNull(result.getTemporaryPassword());
        verify(activityService).record(any(), org.mockito.ArgumentMatchers.eq("ADMIN_CREATED"), any());
    }
}
