package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.repository.PurchaseReturnDetailRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseReturnDetailServiceTest {

    @Test
    void directCreateIsAlwaysBlocked() {
        PurchaseReturnDetailService service = service(mock(PurchaseReturnDetailRepository.class));

        assertThrows(IllegalStateException.class, () -> service.save(new PurchaseReturnDetailDTO()));
    }

    @Test
    void confirmedReturnDetailCannotBeDeleted() {
        PurchaseReturnDetailRepository details = mock(PurchaseReturnDetailRepository.class);
        PurchaseReturn parent = PurchaseReturn.builder().id(7).status("CONFIRMED").build();
        when(details.findById(9)).thenReturn(Optional.of(
                PurchaseReturnDetail.builder().id(9).purchaseReturn(parent).build()));
        PurchaseReturnDetailService service = service(details);

        assertThrows(IllegalStateException.class, () -> service.delete(9));
    }

    private PurchaseReturnDetailService service(PurchaseReturnDetailRepository details) {
        return new PurchaseReturnDetailService(
                details, mock(PurchaseReturnRepository.class), mock(PurchaseReturnMapper.class));
    }
}
