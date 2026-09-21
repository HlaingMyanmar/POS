package org.sspd.servicemgmt.setupoptions;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.companysettingoptions.model.CompanySettings;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.rbacoptions.roleoptions.repository.RoleRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupServiceInitializeGuardTest {

    @Test
    void rejectsInitializeWhenSetupAlreadyComplete() {
        CompanySettingsRepository companySettings = mock(CompanySettingsRepository.class);
        PaymentMethodRepository paymentMethods = mock(PaymentMethodRepository.class);
        UserRepository users = mock(UserRepository.class);

        CompanySettings cs = new CompanySettings();
        cs.setCompanyName("Already set");
        cs.setSetupComplete(true);
        when(companySettings.findAll()).thenReturn(List.of(cs));
        when(paymentMethods.count()).thenReturn(1L);
        when(users.existsByRoleName("ADMINISTRATOR")).thenReturn(true);
        when(users.count()).thenReturn(1L);

        SetupService service = new SetupService(
                paymentMethods,
                mock(ChartOfAccountRepository.class),
                companySettings,
                users,
                mock(RoleRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class)
        );

        SetupInitDTO dto = new SetupInitDTO();
        dto.setCompanyName("Hacker rename");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.initialize(dto));
        assertTrue(ex.getMessage().toLowerCase().contains("already complete"));
        verify(companySettings, never()).save(any());
        verify(paymentMethods, never()).save(any());
    }
}
