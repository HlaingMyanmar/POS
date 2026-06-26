package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.dto.PaymentMethodDTO;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.mapper.PaymentMethodMapper;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final ChartOfAccountRepository coaRepository; // COA စစ်ဆေးရန် လိုအပ်သည်
    private final PaymentMethodMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String PAYMENT_TOPIC = "/topic/payment-method";

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_CREATE')")
    @Transactional
    public PaymentMethodDTO save(PaymentMethodDTO dto) {
        // ၁။ နာမည်တူရှိမရှိ အရင်စစ်ဆေးခြင်း
        if (repository.existsByMethodName(dto.getMethodName())) {
            throw new RuntimeException("Payment Method '" + dto.getMethodName() + "' is already registered!");
        }

        PaymentMethod entity = mapper.toEntity(dto);

        // ၂။ COA Account ချိတ်ဆက်ခြင်း
        if (dto.getAccountId() != null) {
            ChartOfAccount account = coaRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Accounting Entry (COA) not found"));
            entity.setAccount(account);
        }

        PaymentMethod savedEntity = repository.save(entity);
        messagingTemplate.convertAndSend(PAYMENT_TOPIC, "PAYMENT_METHOD_CREATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @Transactional(readOnly = true)
    public List<PaymentMethodDTO> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @Transactional(readOnly = true)
    public List<PaymentMethodDTO> findAllActive() {
        return repository.findAllByActiveTrue()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @Transactional(readOnly = true)
    public PaymentMethodDTO findById(Integer id) {
        PaymentMethod entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method Not Found with id " + id));
        return mapper.toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_UPDATE')")
    @Transactional
    public PaymentMethodDTO update(Integer id, PaymentMethodDTO dto) {
        PaymentMethod existingEntity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method Not Found with id " + id));

        // နာမည်ပြောင်းရင် တခြားမှာ ရှိပြီးသားလား စစ်ဆေးခြင်း
        if (!existingEntity.getMethodName().equals(dto.getMethodName()) &&
                repository.existsByMethodName(dto.getMethodName())) {
            throw new RuntimeException("Payment Method name '" + dto.getMethodName() + "' is already taken!");
        }

        mapper.updateEntityFromDto(dto, existingEntity);

        // COA Account အသစ်ပြောင်းလဲခြင်း
        if (dto.getAccountId() != null) {
            ChartOfAccount account = coaRepository.findById(dto.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Accounting Entry (COA) not found"));
            existingEntity.setAccount(account);
        }

        PaymentMethod savedEntity = repository.save(existingEntity);
        messagingTemplate.convertAndSend(PAYMENT_TOPIC, "PAYMENT_METHOD_UPDATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_DELETE')")
    @Transactional
    public void delete(Integer id) {
        PaymentMethod entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method Not Found with id " + id));

        repository.delete(entity);
        messagingTemplate.convertAndSend(PAYMENT_TOPIC, "PAYMENT_METHOD_DELETED");
    }
}