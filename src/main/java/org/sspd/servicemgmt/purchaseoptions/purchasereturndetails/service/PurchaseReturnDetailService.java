package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.repository.PurchaseReturnDetailRepository;

import java.util.List;

/**
 * Read-only detail API. Confirmed/voided returns must not be mutated here —
 * stock, serials, lots and journals are owned by {@code PurchaseReturnService}.
 */
@Service
@RequiredArgsConstructor
public class PurchaseReturnDetailService {

    private final PurchaseReturnDetailRepository detailRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_CREATE')")
    @Transactional
    public PurchaseReturnDetailDTO save(PurchaseReturnDetailDTO dto) {
        throw new IllegalStateException(
                "Purchase return details cannot be added via this API. Create a new purchase return voucher (or void and recreate).");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseReturnDetailDTO> findAll() {
        return detailRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @Transactional(readOnly = true)
    public PurchaseReturnDetailDTO findById(Integer id) {
        PurchaseReturnDetail detail = detailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return detail not found with id: " + id));
        return mapper.toDto(detail);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_UPDATE')")
    @Transactional
    public PurchaseReturnDetailDTO update(Integer id, PurchaseReturnDetailDTO dto) {
        assertMutableParent(id);
        throw new IllegalStateException(
                "Purchase return details cannot be edited. Void the return and recreate if correction is needed.");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_DELETE')")
    @Transactional
    public void delete(Integer id) {
        assertMutableParent(id);
        throw new IllegalStateException(
                "Purchase return details cannot be deleted. Void the return voucher instead.");
    }

    private void assertMutableParent(Integer detailId) {
        PurchaseReturnDetail existing = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return detail not found with id: " + detailId));
        PurchaseReturn parent = existing.getPurchaseReturn();
        if (parent != null && parent.getStatus() != null
                && !"DRAFT".equalsIgnoreCase(parent.getStatus())) {
            throw new IllegalStateException(
                    "Confirmed/voided purchase return details are locked. Use void workflow on the return voucher.");
        }
    }
}
