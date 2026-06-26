package org.sspd.servicemgmt.staffoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.staffoptions.dto.StaffDTO;
import org.sspd.servicemgmt.staffoptions.mapper.StaffMapper;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository repository;
    private final StaffMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private static final String STAFF_TOPIC = "/topic/staff";

    // ၁။ Staff အသစ်သွင်းခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_CREATE')")
    @Transactional
    public StaffDTO save(StaffDTO dto) {
        if (dto.getPhone() != null && repository.existsByPhone(dto.getPhone())) {
            throw new RuntimeException("Phone number '" + dto.getPhone() + "' is already registered!");
        }
        Staff saved = repository.save(mapper.toEntity(dto));
        messagingTemplate.convertAndSend(STAFF_TOPIC, "STAFF_CREATED");
        return mapper.toDto(saved);
    }

    // ၂။ Staff အားလုံးကိုကြည့်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_READ')")
    @Transactional(readOnly = true)
    public List<StaffDTO> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    // ၃။ လက်ရှိအလုပ်လုပ်နေသော Staff များကိုကြည့်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_READ')")
    @Transactional(readOnly = true)
    public List<StaffDTO> findAllActive() {
        return repository.findAllByIsActiveTrue().stream().map(mapper::toDto).toList();
    }

    // ၄။ ID ဖြင့် တစ်ဦးတည်းကိုရှာခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_READ')")
    @Transactional(readOnly = true)
    public StaffDTO findById(Integer id) {
        Staff staff = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found with id " + id));
        return mapper.toDto(staff);
    }

    // ၅။ Staff အချက်အလက်ပြင်ဆင်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_UPDATE')")
    @Transactional
    public StaffDTO update(Integer id, StaffDTO dto) {
        Staff existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        if (dto.getPhone() != null && !existing.getPhone().equals(dto.getPhone())
                && repository.existsByPhone(dto.getPhone())) {
            throw new RuntimeException("Phone number '" + dto.getPhone() + "' is already in use!");
        }
        existing.setActive(dto.isActive());
        mapper.updateEntityFromDto(dto, existing);
        messagingTemplate.convertAndSend(STAFF_TOPIC, "STAFF_UPDATED");
        return mapper.toDto(repository.save(existing));
    }

    // ၆။ Staff ကို ဖျက်ခြင်း (သို့မဟုတ် Inactive လုပ်ခြင်း)
    @PreAuthorize("hasAuthority('CAN_ACCESS_STAFF_DELETE')")
    @Transactional
    public void delete(Integer id) {
        Staff staff = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        // Data Integrity အတွက် Inactive ပဲလုပ်တာ ပိုကောင်းပါတယ်
        staff.setActive(false);
        repository.save(staff);
        messagingTemplate.convertAndSend(STAFF_TOPIC, "STAFF_DELETED");
    }
}