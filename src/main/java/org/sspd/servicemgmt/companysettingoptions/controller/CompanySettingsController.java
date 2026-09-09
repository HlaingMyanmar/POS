package org.sspd.servicemgmt.companysettingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsTestMailRequest;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerMailService;

@RestController
@RequestMapping("/api/v1/company-settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CompanySettingsController {

    private final CompanySettingsService service;
    private final CustomerMailService customerMailService;

    @GetMapping
    public ResponseEntity<ApiResponse<CompanySettingsDTO>> get() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Company settings", service.getSettings()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanySettingsDTO>> save(@RequestBody CompanySettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", service.saveSettings(dto)));
    }

    @PostMapping("/test-mail")
    public ResponseEntity<ApiResponse<Void>> testMail(@RequestBody CompanySettingsTestMailRequest req) {
        String to = req != null ? req.getTo() : null;
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Test လက်ခံမည့် email ထည့်ပါ", null));
        }
        customerMailService.sendTest(to.trim());
        return ResponseEntity.ok(new ApiResponse<>(true, "Test email ပို့ပြီးပါပြီ", null));
    }
}
