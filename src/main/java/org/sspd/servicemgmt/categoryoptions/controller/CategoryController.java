package org.sspd.servicemgmt.categoryoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.categoryoptions.dto.CategoryDTO;
import org.sspd.servicemgmt.categoryoptions.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/category")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Category List",service.findAll())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>>getfindCategoryTree(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Category Tree List",service.findCategoryTree())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category Found", service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/{id}/sub")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getSubCategories(@PathVariable Long id){
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category Found", service.findSubCategories(id))
        );
    }
    // အသုံးပြုလို့ရတဲ့ (Active) အပေါ်ဆုံးအဆင့် Category များစာရင်း (URL: /api/v1/category/active-roots)
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/active-roots")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getActiveRootCategories() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Active Root Categories Found", service.findActiveRootCategories())
        );
    }

    // ၁။ Active ဖြစ်နေတဲ့ Category အားလုံး (Root ကော Sub ကော) ကို ရှာခြင်း
    // URL: /api/v1/category/all-active
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/all-active")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAllActiveCategories() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "All Active Categories Found", service.findAllActiveCategories())
        );
    }

    // ၂။ Status (Active/Inactive) ကို Query Parameter အနေနဲ့ ပို့ပြီး ရှာခြင်း
    // URL: /api/v1/category/filter?status=true သို့မဟုတ် /api/v1/category/filter?status=false
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_READ')")
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getCategoriesByStatus(@RequestParam boolean status) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Categories Found with status: " + status, service.findCategoriesByStatus(status))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryDTO>> createCategory(
            @Valid @RequestBody CategoryDTO dto){
        CategoryDTO created  = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Category Created Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryDTO>>updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryDTO dto){

        CategoryDTO update=  service.update(id,dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Category Updated Successfully",update)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_CATEGORY_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Category Deleted Successfully", null)
        );
    }

}
