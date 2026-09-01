package org.sspd.servicemgmt.exportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.exportoptions.service.ExcelExportService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/export")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExcelExportController {

    private final ExcelExportService exportService;

    @GetMapping("/services")
    ResponseEntity<byte[]> exportServices() {
        byte[] data = exportService.exportServices();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=services_" + LocalDate.now() + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }
}
