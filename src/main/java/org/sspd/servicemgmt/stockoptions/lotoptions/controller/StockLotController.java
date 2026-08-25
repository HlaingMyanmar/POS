package org.sspd.servicemgmt.stockoptions.lotoptions.controller;
import lombok.RequiredArgsConstructor;import org.springframework.http.ResponseEntity;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import org.sspd.servicemgmt.api.ApiResponse;import org.sspd.servicemgmt.stockoptions.lotoptions.dto.StockLotDTO;import org.sspd.servicemgmt.stockoptions.lotoptions.service.StockLotService;import java.util.*;
@RestController @RequestMapping("/api/v1/stock-lots")@RequiredArgsConstructor
public class StockLotController{
 private final StockLotService service;
 @GetMapping("/expiring")@PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_EXPIRY','CAN_ACCESS_STOCK_READ')")
 public ResponseEntity<ApiResponse<List<StockLotDTO>>> expiring(@RequestParam(defaultValue="90")int days){return ResponseEntity.ok(new ApiResponse<>(true,"Expiring stock lots",service.expiring(days)));}
 @GetMapping("/warehouse-balances")@PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_WAREHOUSE','CAN_ACCESS_STOCK_READ')")
 public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.stockoptions.lotoptions.dto.WarehouseBalanceDTO>>> warehouseBalances(){return ResponseEntity.ok(new ApiResponse<>(true,"Warehouse stock balances",service.warehouseBalances()));}
}
