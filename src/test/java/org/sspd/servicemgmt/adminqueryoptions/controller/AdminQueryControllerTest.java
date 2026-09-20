package org.sspd.servicemgmt.adminqueryoptions.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sspd.servicemgmt.adminqueryoptions.config.AdminQueryProperties;
import org.sspd.servicemgmt.adminqueryoptions.service.AdminQueryDisabledException;
import org.sspd.servicemgmt.adminqueryoptions.service.AdminQueryService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminQueryControllerTest {

    @Test
    void statusReportsDisabledByDefault() throws Exception {
        AdminQueryProperties properties = new AdminQueryProperties();
        MockMvc mvc = mvc(mock(AdminQueryService.class), properties);

        mvc.perform(get("/api/v1/admin-queries/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void disabledCatalogReturnsControlledForbiddenResponse() throws Exception {
        AdminQueryService service = mock(AdminQueryService.class);
        when(service.listQueries()).thenThrow(new AdminQueryDisabledException());
        MockMvc mvc = mvc(service, new AdminQueryProperties());

        mvc.perform(get("/api/v1/admin-queries"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("SQL Console is disabled on this server."));
    }

    private static MockMvc mvc(AdminQueryService service, AdminQueryProperties properties) {
        return MockMvcBuilders
                .standaloneSetup(new AdminQueryController(service, properties))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }
}
