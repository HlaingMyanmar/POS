package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentTransactionMapperNullDefaultsTest {

    @Test
    void toEntityMapsNullReversedToFalse() {
        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setAmount(java.math.BigDecimal.TEN);
        dto.setReversed(null);

        PaymentTransaction entity = PaymentTransactionMapper.INSTANCE.toEntity(dto);

        assertEquals(Boolean.FALSE, entity.getReversed());
        assertFalse(Boolean.TRUE.equals(entity.getReversed()));
    }
}
