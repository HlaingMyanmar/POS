package org.sspd.servicemgmt.categoryoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.categoryoptions.dto.CategoryDTO;
import org.sspd.servicemgmt.categoryoptions.mapper.CategoryMapper;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final SimpMessagingTemplate messagingTemplate;
    private static final String CATEGORY_TOPIC = "/topic/category";
    private final CategoryRepository categoryRepository;
    private final CategoryMapper mapper;

    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_CREATE')")
    @Transactional
    public CategoryDTO save(CategoryDTO dto){
        if (categoryRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Category '" + dto.getName() + "' is already registered!");
        }
        Category entity = mapper.toEntity(dto);
        if (dto.getId() != null) {
            Category parent = categoryRepository.findById(dto.getId().longValue())
                    .orElseThrow(() -> new RuntimeException("Parent Category not found with id: " + dto.getId()));
            entity.setParent(parent);
        }
        Category savedEntity = categoryRepository.save(entity);
        messagingTemplate.convertAndSend(CATEGORY_TOPIC, "CATEGORY_CREATED");
        return mapper.toDto(savedEntity);

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO>findAll(){
        return categoryRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO>findCategoryTree(){
        return categoryRepository.findAllByParentIsNull()
                .stream()
                .map(mapper::toDto)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public CategoryDTO findById(Long id){
        Category entity = categoryRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Category Not found with id :"+id));
        return mapper.toDto(entity);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO> findSubCategories(Long parentId) {
        // Parent ID တစ်ခုပေးလိုက်ရင် သူ့အောက်က sub-categories တွေကိုပဲ ထုတ်ပြမယ်
        return categoryRepository.findAllByParentId(parentId)
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO> findActiveRootCategories() {
        // အသုံးပြုလို့ရတဲ့ အပေါ်ဆုံးအဆင့် Category တွေကိုပဲ list ထုတ်ပေးမယ်
        return categoryRepository.findAllByParentIsNullAndIsActiveTrue()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO> findAllActiveCategories() {
        return categoryRepository.findAllByIsActiveTrue()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @Transactional(readOnly = true)
    public List<CategoryDTO> findCategoriesByStatus(boolean status) {
        return categoryRepository.findAllByIsActive(status)
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_UPDATE')")
    @Transactional
    public CategoryDTO update(Long id, CategoryDTO categoryDTO) {
        Category existingEntity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category Not found with id :" + id));

        // နာမည်တူစစ်ခြင်း
        if (!existingEntity.getName().equals(categoryDTO.getName()) &&
                categoryRepository.existsByName(categoryDTO.getName())) {
            throw new RuntimeException("Category name '" + categoryDTO.getName() + "' is already taken!");
        }

        // Self-parenting စစ်ခြင်း (parentId ကို သုံးပါ)
        if (categoryDTO.getParentId() != null && categoryDTO.getParentId().longValue() == id) {
            throw new RuntimeException("A category cannot be its own parent!");
        }

        // Mapper က active ရော တခြား field တွေရောကို အလိုအလျောက် update လုပ်ပေးပါလိမ့်မယ်
        mapper.updateEntityFromDto(categoryDTO, existingEntity);

        // Parent ကို manual ချိတ်ပေးခြင်း
        if (categoryDTO.getParentId() != null) {
            Category parent = categoryRepository.findById(categoryDTO.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category not found"));
            existingEntity.setParent(parent);
        } else {
            existingEntity.setParent(null);
        }

        Category savedEntity = categoryRepository.save(existingEntity);
        messagingTemplate.convertAndSend(CATEGORY_TOPIC, "CATEGORY_UPDATE");
        return mapper.toDto(savedEntity);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_DELETE')")
    @Transactional
    public void delete(Long id) {
        Category existingEntity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category Not found with id :" + id));
        existingEntity.setActive(false);
        categoryRepository.save(existingEntity);

        messagingTemplate.convertAndSend(CATEGORY_TOPIC, "CATEGORY_DELETE");
    }
}
