package org.sspd.servicemgmt.accountingoptions.coaoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.dto.ChartOfAccountDTO;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.mapper.ChartOfAccountMapper;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;

import java.util.List;
@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChartOfAccountRepository repository;
    private final ChartOfAccountMapper mapper;
    private final JournalDetailRepository journalDetailRepository; // ← ထည့်

    private static final String COA_TOPIC = "/topic/coa";

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_CREATE')")
    @Transactional
    public ChartOfAccountDTO save(ChartOfAccountDTO dto) {
        if (repository.existsByAccountName(dto.getAccountName())) {
            throw new RuntimeException(
                    "Account Name '" + dto.getAccountName() + "' already exists!");
        }

        ChartOfAccount entity = mapper.toEntity(dto);

        if (dto.getParentId() != null) {
            ChartOfAccount parent = repository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Account not found"));
            entity.setParent(parent);
            entity.setAccountType(parent.getAccountType());
        }

        // ✅ Code auto-generate — COA prefix ကို accountType မှ မဟုတ်ဘဲ
        //    ရှိပြီးသား codes များနှင့် collision မဖြစ်အောင် sequence ဆောက်
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            entity.setCode(generateUniqueCode(entity.getAccountType()));
        } else if (repository.existsByCode(entity.getCode())) {
            throw new RuntimeException(
                    "Account Code '" + entity.getCode() + "' already exists!");
        }

        ChartOfAccount saved = repository.save(entity);
        messagingTemplate.convertAndSend(COA_TOPIC, "COA_CREATED");
        return mapper.toDto(saved);
    }

    // ✅ Unique code generator
    private String generateUniqueCode(AccountType type) {
        String prefix = switch (type) {
            case Asset     -> "ASS";
            case Liability -> "LIA";
            case Income    -> "INC";
            case Expense   -> "EXP";
            case Equity    -> "EQU";
        };

        // ရှိပြီးသား codes များထဲမှ ထို prefix ဖြင့် စသော max number ရှာ
        int next = repository.findAll().stream()
                .map(ChartOfAccount::getCode)
                .filter(c -> c != null && c.startsWith(prefix + "-"))
                .mapToInt(c -> {
                    try {
                        return Integer.parseInt(c.substring(prefix.length() + 1));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0) + 1;

        return String.format("%s-%03d", prefix, next);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @Transactional(readOnly = true)
    public List<ChartOfAccountDTO> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @Transactional(readOnly = true)
    public List<ChartOfAccountDTO> findTree() {
        return repository.findAllByParentIsNull().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @Transactional(readOnly = true)
    public ChartOfAccountDTO findById(Integer id) {
        return mapper.toDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("COA not found: " + id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_UPDATE')")
    @Transactional
    public ChartOfAccountDTO update(Integer id, ChartOfAccountDTO dto) {
        ChartOfAccount existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("COA not found: " + id));

        if (!existing.getAccountName().equals(dto.getAccountName()) &&
                repository.existsByAccountName(dto.getAccountName())) {
            throw new RuntimeException("Account name '" + dto.getAccountName() + "' already taken!");
        }
        if (dto.getParentId() != null && dto.getParentId().equals(id)) {
            throw new RuntimeException("Account cannot be its own parent!");
        }

        // ✅ Code ပြောင်းလျှင် collision စစ်
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())
                && repository.existsByCode(dto.getCode())) {
            throw new RuntimeException("Account code '" + dto.getCode() + "' already exists!");
        }

        // ✅ System accounts (AccountCode constants) ကို type ပြောင်းခွင့်မပေး
        if (dto.getAccountType() != null &&
                !dto.getAccountType().equals(existing.getAccountType()) &&
                isSystemAccount(existing.getCode())) {
            throw new RuntimeException(
                    "Cannot change type of system account: " + existing.getCode());
        }

        mapper.updateEntityFromDto(dto, existing);

        if (dto.getParentId() != null) {
            ChartOfAccount parent = repository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Account not found"));
            existing.setParent(parent);
            existing.setAccountType(parent.getAccountType());
        } else {
            existing.setParent(null);
        }

        ChartOfAccount saved = repository.save(existing);
        messagingTemplate.convertAndSend(COA_TOPIC, "COA_UPDATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_DELETE')")
    @Transactional
    public void delete(Integer id) {
        ChartOfAccount existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("COA not found: " + id));

        // ၁။ System account ဖျက်ခွင့်မပေး
        if (isSystemAccount(existing.getCode())) {
            throw new RuntimeException(
                    "Cannot delete system account: " + existing.getCode() +
                            ". This account is used by the accounting engine.");
        }

        // ၂။ Child accounts ရှိလျှင် ဖျက်ခွင့်မပေး
        if (!existing.getChildren().isEmpty()) {
            throw new RuntimeException(
                    "Cannot delete account with sub-accounts. Delete children first.");
        }

        // ✅ ၃။ Journal entries တွင် သုံးနေသေးလျှင် ဖျက်ခွင့်မပေး
        if (journalDetailRepository.existsByAccountId(id)) {
            throw new RuntimeException(
                    "Cannot delete account '" + existing.getAccountName() +
                            "'. It has existing journal entries. Consider deactivating instead.");
        }

        repository.delete(existing);
        messagingTemplate.convertAndSend(COA_TOPIC, "COA_DELETED");
    }

    // ✅ AccountCode constants တွင် ပါသော system accounts များ
    private boolean isSystemAccount(String code) {
        return code != null && List.of(
                "ASS-002","ASS-003","ASS-004","ASS-005","ASS-006","ASS-007",
                "LIA-002","LIA-003","LIA-004",
                "INC-002","INC-003","INC-006","INC-007","INC-008",
                "EXP-006","EXP-007","EXP-010","EXP-011","EXP-012","EXP-013",
                "EQU-001","EQU-002","EQU-003"
        ).contains(code);
    }
}