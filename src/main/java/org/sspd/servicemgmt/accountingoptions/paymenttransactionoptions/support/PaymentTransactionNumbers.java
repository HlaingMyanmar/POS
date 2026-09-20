package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.support;

import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;

public final class PaymentTransactionNumbers {
    private PaymentTransactionNumbers() {}

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String documentNo(String prefix, Integer id) {
        return String.format("%s-%06d", prefix, id);
    }

    public static PaymentTransaction save(PaymentTransactionRepository repository, PaymentTransaction entity) {
        entity.setTransactionNo(blankToNull(entity.getTransactionNo()));
        PaymentTransaction saved = repository.save(entity);
        if (saved.getId() != null && (saved.getTransactionNo() == null || saved.getTransactionNo().isBlank())) {
            saved.assignGeneratedNumberIfBlank();
            saved = repository.save(saved);
        }
        return saved;
    }
}
