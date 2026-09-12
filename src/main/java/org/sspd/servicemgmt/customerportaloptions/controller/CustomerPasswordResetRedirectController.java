package org.sspd.servicemgmt.customerportaloptions.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves a standalone reset page (not the POS /shop SPA) so email links
 * do not expose other web pages.
 */
@Controller
public class CustomerPasswordResetRedirectController {

    @GetMapping("/customer/reset-password")
    public String resetPage() {
        return "forward:/customer-reset-password.html";
    }
}
