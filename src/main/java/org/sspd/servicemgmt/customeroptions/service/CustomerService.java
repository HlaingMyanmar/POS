package org.sspd.servicemgmt.customeroptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.dto.CustomerDTO;
import org.sspd.servicemgmt.customeroptions.mapper.CustomerMapper;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String CUSTOMER_TOPIC = "/topic/customer";

    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_CREATE')")
    @Transactional
    public CustomerDTO save(CustomerDTO dto) {
        // ဖုန်းနံပါတ် ရှိနှင့်ပြီးသားလား စစ်မယ် (Unique Constraint)
        if (repository.existsByPhone(dto.getPhone())) {
            throw new RuntimeException("Phone number '" + dto.getPhone() + "' is already registered!");
        }

        Customer entity = mapper.toEntity(dto);
        entity.setCreditHold(Boolean.TRUE);
        entity.setCreditHoldReason("New customer – pending credit review");
        Customer savedEntity = repository.save(entity);

        messagingTemplate.convertAndSend(CUSTOMER_TOPIC, "CUSTOMER_CREATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_READ')")
    @Transactional(readOnly = true)
    public List<CustomerDTO> findAll() {
        return repository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_READ')")
    @Transactional(readOnly = true)
    public CustomerDTO findById(Integer id) {
        Customer entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer Not Found with id " + id));
        return mapper.toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_UPDATE')")
    @Transactional
    public CustomerDTO update(Integer id, CustomerDTO dto) {
        Customer existingEntity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer Not Found with id " + id));

        // ဖုန်းနံပါတ် အသစ်ပြောင်းရင် တခြားသူနဲ့ သွားတူနေလား စစ်မယ်
        if (!existingEntity.getPhone().equals(dto.getPhone()) && repository.existsByPhone(dto.getPhone())) {
            throw new RuntimeException("Phone number '" + dto.getPhone() + "' is already in use by another customer!");
        }

        mapper.updateEntityFromDto(dto, existingEntity);
        if (dto.getCreditHold() != null) {
            existingEntity.setCreditHold(dto.getCreditHold());
        }
        if (dto.getCreditHoldReason() != null) {
            existingEntity.setCreditHoldReason(dto.getCreditHoldReason());
        }
        if (dto.getBlacklisted() != null) {
            existingEntity.setBlacklisted(dto.getBlacklisted());
        }
        if (dto.getBlacklistReason() != null) {
            existingEntity.setBlacklistReason(dto.getBlacklistReason());
        }
        Customer savedEntity = repository.save(existingEntity);

        messagingTemplate.convertAndSend(CUSTOMER_TOPIC, "CUSTOMER_UPDATED");
        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_DELETE')")
    @Transactional
    public void delete(Integer id) {
        Customer entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer Not Found with id " + id));

        // တကယ်လို့ ဒီ Customer က Booking တင်ထားတာရှိရင် ဖျက်လို့မရအောင် logic ထည့်လို့ရပါတယ်
        repository.delete(entity);
        messagingTemplate.convertAndSend(CUSTOMER_TOPIC, "CUSTOMER_DELETED");
    }
}
