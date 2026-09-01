package org.sspd.servicemgmt.serviceoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceItemDTO;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceItemPriceHistoryDTO;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItemPriceHistory;
import org.sspd.servicemgmt.serviceoptions.model.ServiceType;
import org.sspd.servicemgmt.serviceoptions.model.SubServiceType;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobLineRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceItemPriceHistoryRepository;
import org.sspd.servicemgmt.serviceoptions.repository.ServiceTypeRepository;
import org.sspd.servicemgmt.serviceoptions.repository.SubServiceTypeRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private static final String SERVICE_TOPIC = "/topic/service";

    private final ServiceItemRepository repository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final SubServiceTypeRepository subServiceTypeRepository;
    private final ServiceJobLineRepository serviceJobLineRepository;
    private final ServiceItemPriceHistoryRepository priceHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findActive() {
        return repository.findByIsActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItemDTO> findByType(Integer typeId) {
        return repository.findByServiceTypeId(typeId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItemPriceHistoryDTO> findPriceHistory(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Service not found: " + id);
        return priceHistoryRepository.findByServiceItemIdOrderByChangedAtDesc(id).stream()
            .map(h -> ServiceItemPriceHistoryDTO.builder()
                .id(h.getId()).oldPrice(h.getOldPrice()).newPrice(h.getNewPrice())
                .oldCost(h.getOldCost()).newCost(h.getNewCost())
                .changedBy(h.getChangedBy()).changedAt(h.getChangedAt()).build())
            .toList();
    }

    @Transactional
    public ServiceItemDTO save(ServiceItemDTO dto) {
        validate(dto, null);
        ServiceType type = serviceTypeRepository.findById(dto.getServiceTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found"));
        Integer subTypeId = dto.getSubServiceTypeId();
        SubServiceType subType = subTypeId != null
            ? subServiceTypeRepository.findById(subTypeId).orElse(null)
            : null;
        String code = generateCode();
        ServiceItem e = ServiceItem.builder()
            .code(code)
            .item(dto.getItem())
            .price(dto.getPrice())
            .costPrice(nz(dto.getCostPrice()))
            .warrantyMonths(nz(dto.getWarrantyMonths()))
            .durationMinutes(nz(dto.getDurationMinutes()))
            .description(dto.getDescription())
            .focDefault(Boolean.TRUE.equals(dto.getFocDefault()))
            .taxRate(nz(dto.getTaxRate()))
            .skillRequired(dto.getSkillRequired())
            .minPrice(dto.getMinPrice())
            .maxPrice(dto.getMaxPrice())
            .commissionPercent(nz(dto.getCommissionPercent()))
            .supportedDeviceTypes(dto.getSupportedDeviceTypes())
            .defaultRequiredParts(dto.getDefaultRequiredParts())
            .isActive(true)
            .serviceType(type)
            .subServiceType(subType)
            .build();
        ServiceItemDTO saved = toDto(repository.save(e));
        broadcast("SERVICE_ITEM_CREATED");
        return saved;
    }

    @Transactional
    public ServiceItemDTO update(Integer id, ServiceItemDTO dto) {
        validate(dto, id);
        ServiceItem e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        ServiceType type = serviceTypeRepository.findById(dto.getServiceTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("ServiceType not found"));
        Integer subTypeId = dto.getSubServiceTypeId();
        SubServiceType subType = subTypeId != null
            ? subServiceTypeRepository.findById(subTypeId).orElse(null)
            : null;
        BigDecimal oldPrice = e.getPrice();
        BigDecimal oldCost = nz(e.getCostPrice());
        BigDecimal newPrice = dto.getPrice();
        BigDecimal newCost = nz(dto.getCostPrice());
        e.setItem(dto.getItem());
        e.setPrice(dto.getPrice());
        e.setCostPrice(nz(dto.getCostPrice()));
        e.setWarrantyMonths(nz(dto.getWarrantyMonths()));
        e.setDurationMinutes(nz(dto.getDurationMinutes()));
        e.setDescription(dto.getDescription());
        e.setFocDefault(Boolean.TRUE.equals(dto.getFocDefault()));
        e.setTaxRate(nz(dto.getTaxRate()));
        e.setSkillRequired(dto.getSkillRequired());
        e.setMinPrice(dto.getMinPrice());
        e.setMaxPrice(dto.getMaxPrice());
        e.setCommissionPercent(nz(dto.getCommissionPercent()));
        e.setSupportedDeviceTypes(dto.getSupportedDeviceTypes());
        e.setDefaultRequiredParts(dto.getDefaultRequiredParts());
        e.setActive(dto.isActive());
        e.setServiceType(type);
        e.setSubServiceType(subType);
        ServiceItem savedEntity = repository.save(e);
        if (!sameAmount(oldPrice, newPrice) || !sameAmount(oldCost, newCost)) {
            priceHistoryRepository.save(ServiceItemPriceHistory.builder()
                .serviceItem(savedEntity).oldPrice(oldPrice).newPrice(newPrice)
                .oldCost(oldCost).newCost(newCost)
                .changedBy(currentUsername()).changedAt(LocalDateTime.now()).build());
        }
        broadcast("SERVICE_ITEM_UPDATED");
        return toDto(savedEntity);
    }

    @Transactional
    public void delete(Integer id) {
        ServiceItem e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
        if (serviceJobLineRepository.existsByServiceItem_Id(id)) {
            throw new IllegalStateException("ဤဝန်ဆောင်မှုကို Job တွင် သုံးထားသဖြင့် ဖျက်မရပါ။ Inactive အဖြစ်ပြောင်းပါ။");
        }
        e.setActive(false);
        repository.save(e);
        broadcast("SERVICE_ITEM_UPDATED");
    }

    private void broadcast(String event) {
        messagingTemplate.convertAndSend(SERVICE_TOPIC, event);
    }

    private void validate(ServiceItemDTO dto, Integer editingId) {
        if (dto.getItem() == null || dto.getItem().isBlank())
            throw new IllegalArgumentException("ဝန်ဆောင်မှုအမည် ဖြည့်ပါ။");
        dto.setItem(dto.getItem().trim());
        boolean duplicate = editingId == null
                ? repository.existsByItemIgnoreCase(dto.getItem())
                : repository.existsByItemIgnoreCaseAndIdNot(dto.getItem(), editingId);
        if (duplicate) throw new IllegalArgumentException("ဝန်ဆောင်မှုအမည် ရှိပြီးသားဖြစ်သည်။");
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("ရောင်းဈေးသည် သုည သို့မဟုတ် အပေါင်းဖြစ်ရပါမည်။");
        if (nz(dto.getCostPrice()).compareTo(BigDecimal.ZERO) < 0 || nz(dto.getWarrantyMonths()) < 0 || nz(dto.getDurationMinutes()) < 0)
            throw new IllegalArgumentException("Cost, warranty နှင့် duration တန်ဖိုးများ အနုတ်မဖြစ်ရပါ။");
        BigDecimal tax = nz(dto.getTaxRate());
        if (tax.compareTo(BigDecimal.ZERO) < 0 || tax.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Tax rate သည် 0 မှ 100 အတွင်းဖြစ်ရပါမည်။");
        BigDecimal minPrice = dto.getMinPrice();
        BigDecimal maxPrice = dto.getMaxPrice();
        if ((minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0)
                || (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0))
            throw new IllegalArgumentException("Minimum and maximum prices cannot be negative.");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price.");
        if (minPrice != null && dto.getPrice().compareTo(minPrice) < 0)
            throw new IllegalArgumentException("ပုံမှန်ဈေးသည် အနည်းဆုံးဈေးထက် မနည်းရပါ။");
        if (maxPrice != null && dto.getPrice().compareTo(maxPrice) > 0)
            throw new IllegalArgumentException("ပုံမှန်ဈေးသည် အများဆုံးဈေးထက် မကျော်ရပါ။");
        BigDecimal commission = nz(dto.getCommissionPercent());
        if (commission.compareTo(BigDecimal.ZERO) < 0 || commission.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Commission percent must be between 0 and 100.");
        if (dto.getSubServiceTypeId() != null) {
            SubServiceType sub = subServiceTypeRepository.findById(dto.getSubServiceTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sub service type not found"));
            if (sub.getServiceType() == null || !sub.getServiceType().getId().equals(dto.getServiceTypeId()))
                throw new IllegalArgumentException("Sub category သည် ရွေးထားသော service type နှင့်မကိုက်ညီပါ။");
        }
    }
    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static Integer nz(Integer v) { return v != null ? v : 0; }
    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return Objects.equals(a, b) || (a != null && b != null && a.compareTo(b) == 0);
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "SYSTEM";
    }

    private String generateCode() {
        int next = repository.findTopByOrderByIdDesc()
            .map(s -> s.getId() + 1).orElse(1);
        return String.format("SVC-%04d", next);
    }

    private ServiceItemDTO toDto(ServiceItem e) {
        ServiceItemDTO dto = new ServiceItemDTO();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setItem(e.getItem());
        dto.setPrice(e.getPrice());
        dto.setCostPrice(e.getCostPrice());
        dto.setWarrantyMonths(e.getWarrantyMonths());
        dto.setDurationMinutes(e.getDurationMinutes());
        dto.setDescription(e.getDescription());
        dto.setFocDefault(Boolean.TRUE.equals(e.getFocDefault()));
        dto.setTaxRate(e.getTaxRate());
        dto.setSkillRequired(e.getSkillRequired());
        dto.setMinPrice(e.getMinPrice());
        dto.setMaxPrice(e.getMaxPrice());
        dto.setCommissionPercent(e.getCommissionPercent());
        dto.setSupportedDeviceTypes(e.getSupportedDeviceTypes());
        dto.setDefaultRequiredParts(e.getDefaultRequiredParts());
        dto.setActive(e.isActive());
        dto.setServiceTypeId(e.getServiceType().getId());
        dto.setServiceTypeName(e.getServiceType().getName());
        if (e.getSubServiceType() != null) {
            dto.setSubServiceTypeId(e.getSubServiceType().getId());
            dto.setSubServiceTypeName(e.getSubServiceType().getName());
        }
        return dto;
    }
}
