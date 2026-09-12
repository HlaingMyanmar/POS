package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppActivityDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppAccount;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerAppActivity;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerAppActivityRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAppActivityService {

    private final CustomerAppActivityRepository activityRepository;

    /** Bump login stats on the account (caller must save) and write an activity row. */
    public void recordSession(CustomerAppAccount account, String action, String detail) {
        if (account == null || account.getId() == null) return;
        account.setLastLoginAt(LocalDateTime.now());
        int count = account.getLoginCount() == null ? 0 : account.getLoginCount();
        account.setLoginCount(count + 1);
        record(account, action, detail);
    }

    public void record(CustomerAppAccount account, String action, String detail) {
        if (account == null || account.getId() == null || action == null || action.isBlank()) return;
        Integer customerId = account.getCustomer() != null ? account.getCustomer().getId() : null;
        if (customerId == null) return;
        String clipped = detail == null ? null : detail.trim();
        if (clipped != null && clipped.length() > 500) clipped = clipped.substring(0, 500);
        activityRepository.save(CustomerAppActivity.builder()
                .accountId(account.getId())
                .customerId(customerId)
                .action(action.trim().toUpperCase())
                .detail(clipped == null || clipped.isBlank() ? null : clipped)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CustomerAppActivityDTO> forAccount(Integer accountId) {
        return activityRepository.findTop100ByAccountIdOrderByCreatedAtDescIdDesc(accountId).stream()
                .map(this::toDto)
                .toList();
    }

    private CustomerAppActivityDTO toDto(CustomerAppActivity row) {
        CustomerAppActivityDTO dto = new CustomerAppActivityDTO();
        dto.setId(row.getId());
        dto.setAccountId(row.getAccountId());
        dto.setCustomerId(row.getCustomerId());
        dto.setAction(row.getAction());
        dto.setDetail(row.getDetail());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }
}
