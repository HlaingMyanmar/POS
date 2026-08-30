package org.sspd.servicemgmt.stockoptions.productoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ImportResultDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.PriceHistoryDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ReorderSuggestionDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.service.ProductService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    private final org.sspd.servicemgmt.stockoptions.lotoptions.service.InventoryStockService inventoryStockService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product List", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getLowStockProducts() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Low Stock Product List", service.findLowStock())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/reorder-suggestions")
    public ResponseEntity<ApiResponse<List<ReorderSuggestionDTO>>> getReorderSuggestions() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reorder suggestions", service.reorderSuggestions()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_PRICE_HISTORY_READ')")
    @GetMapping("/{id}/price-history")
    public ResponseEntity<ApiResponse<List<PriceHistoryDTO>>> getPriceHistory(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product price history", service.priceHistory(id)));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO>> getProductById(
            @PathVariable Integer id
    ){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product Found",service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductDTO>>createProduct(
            @Valid @RequestBody ProductDTO dto
    ){
        ProductDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Product Created Successfully", created)
        );

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO>>updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductDTO dto
    ){
        ProductDTO update =service.update(id,dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product Updated Successfully",update)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>>deleteProduct(@PathVariable Integer id){
        service.archive(id);
        return ResponseEntity.ok(
            new ApiResponse<>(true, "Product Archived Successfully",null)
        );
    }

        @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
        @PutMapping("/{id}/archive")
        public ResponseEntity<ApiResponse<ProductDTO>> setArchived(
            @PathVariable Integer id,
            @RequestParam boolean archived) {
        return ResponseEntity.ok(new ApiResponse<>(true, archived ? "Product Archived" : "Product Restored", service.setArchived(id, archived)));
        }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}/next-serial-seq")
    public ResponseEntity<ApiResponse<Integer>> getNextSerialSeq(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Next serial sequence", service.getNextSerialSeq(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PutMapping("/{id}/photo")
    public ResponseEntity<ApiResponse<Void>> updatePhoto(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> body) {
        service.updatePhoto(id, body.get("photoBase64"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Photo updated", null));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] bytes = service.exportExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"products.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] bytes = service.downloadTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"product_import_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_CREATE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResultDTO>> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportResultDTO result = service.importExcel(file);
        return ResponseEntity.ok(new ApiResponse<>(true, "Import completed", result));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PostMapping("/{id}/assign-serials")
    public ResponseEntity<ApiResponse<ProductDTO>> assignSerials(
            @PathVariable Integer id,
            @RequestBody AssignSerialsRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Serials assigned",
                service.assignSerials(id, req.getSerialNumbers(), req.getWarrantyMonths())));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}/warehouse-stocks")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.stockoptions.lotoptions.dto.WarehouseStockDTO>>> warehouseStocks(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Warehouse stocks", inventoryStockService.warehouseStocks(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}/lots")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.stockoptions.lotoptions.dto.StockLotDTO>>> productLots(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product lots", inventoryStockService.lotsForProduct(id)));
    }

    static class AssignSerialsRequest {
        private List<String> serialNumbers;
        private Integer warrantyMonths;
        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
        public Integer getWarrantyMonths() { return warrantyMonths; }
        public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    }

}
