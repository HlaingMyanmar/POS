package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseOrderReceiverIdentityTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receiverUsesAuthenticatedLinkedStaffInsteadOfPoRequester() throws Exception {
        StaffRepository staffRepository = mock(StaffRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        Staff authenticatedStaff = Staff.builder().id(22).name("Receiver").role("Warehouse").isActive(true).build();
        User user = new User();
        user.setStaff(authenticatedStaff);
        when(userRepository.findByUsernameOrEmail("receiver", "receiver")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("receiver", "n/a",
                        List.of(new SimpleGrantedAuthority("CAN_ACCESS_PURCHASE_ORDER_RECEIVE"))));
        PurchaseOrderService service = construct(Map.of(
                StaffRepository.class, staffRepository,
                UserRepository.class, userRepository));

        Method resolver = PurchaseOrderService.class.getDeclaredMethod("resolveReceiverStaff", Integer.class);
        resolver.setAccessible(true);
        Staff resolved = (Staff) resolver.invoke(service, 11);

        assertEquals(22, resolved.getId());
    }

    private PurchaseOrderService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseOrderService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseOrderService) constructor.newInstance(args);
    }
}
