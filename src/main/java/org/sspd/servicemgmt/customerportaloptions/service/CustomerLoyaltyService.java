package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerLoyaltyDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrder;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CustomerLoyaltyService {
    private static final int KYAT_PER_POINT = 1000;
    private final CustomerRepository customers;

    @Transactional
    public void award(CustomerOrder order, BigDecimal paidAmount) {
        if (order == null || order.isLoyaltyAwarded() || paidAmount == null || paidAmount.signum() <= 0) return;
        int earned = paidAmount.divide(BigDecimal.valueOf(KYAT_PER_POINT), 0, RoundingMode.FLOOR).intValue();
        if (earned > 0) {
            var customer = order.getCustomer();
            customer.setLoyaltyPoints(safe(customer.getLoyaltyPoints()) + earned);
            customer.setLoyaltyTotalEarned(safe(customer.getLoyaltyTotalEarned()) + earned);
            customers.save(customer);
        }
        order.setLoyaltyAwarded(true);
    }

    @Transactional(readOnly = true)
    public CustomerLoyaltyDTO mine() {
        int id = CustomerPortalAuth.require().getCustomerId();
        var customer = customers.findById(id).orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        int current = safe(customer.getLoyaltyPoints());
        int total = safe(customer.getLoyaltyTotalEarned());
        String tier = total >= 5000 ? "Platinum" : total >= 2000 ? "Gold" : total >= 500 ? "Silver" : "Bronze";
        int next = total >= 5000 ? 0 : total >= 2000 ? 5000 - total : total >= 500 ? 2000 - total : 500 - total;
        return new CustomerLoyaltyDTO(current, total, next, tier);
    }

    private int safe(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
