package org.sspd.servicemgmt.purchaseoptions.budget.controller;
import lombok.RequiredArgsConstructor; import org.springframework.http.ResponseEntity; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse; import org.sspd.servicemgmt.purchaseoptions.budget.dto.PurchaseBudgetDTO;
import org.sspd.servicemgmt.purchaseoptions.budget.service.PurchaseBudgetService; import java.util.*;
@RestController @RequestMapping("/api/v1/purchase-budgets") @RequiredArgsConstructor
public class PurchaseBudgetController {
 private final PurchaseBudgetService service;
 @GetMapping @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')") public ResponseEntity<ApiResponse<List<PurchaseBudgetDTO>>> list(){return ResponseEntity.ok(new ApiResponse<>(true,"Purchase budgets",service.list()));}
 @PostMapping @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')") public ResponseEntity<ApiResponse<PurchaseBudgetDTO>> save(@RequestBody PurchaseBudgetDTO body){return ResponseEntity.status(201).body(new ApiResponse<>(true,"Budget saved",service.save(body)));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')") public ResponseEntity<ApiResponse<PurchaseBudgetDTO>> update(@PathVariable Integer id,@RequestBody PurchaseBudgetDTO body){body.setId(id);return ResponseEntity.ok(new ApiResponse<>(true,"Budget updated",service.save(body)));}
 @PostMapping("/{id}/active") @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')") public ResponseEntity<ApiResponse<PurchaseBudgetDTO>> active(@PathVariable Integer id,@RequestParam boolean value){return ResponseEntity.ok(new ApiResponse<>(true,"Budget status updated",service.toggle(id,value)));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_BUDGET')") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id){service.delete(id);return ResponseEntity.ok(new ApiResponse<>(true,"Budget deleted",null));}
}
