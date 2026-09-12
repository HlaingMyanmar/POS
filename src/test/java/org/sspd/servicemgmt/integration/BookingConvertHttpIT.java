package org.sspd.servicemgmt.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
@WithMockUser(username = "it-admin", authorities = {
        "CAN_ACCESS_BOOKING_CREATE",
        "CAN_ACCESS_BOOKING_CONVERT_JOB",
        "CAN_ACCESS_BOOKING_READ",
        "CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN",
        "CAN_ACCESS_SERVICE_JOB_CREATE"
})
class BookingConvertHttpIT extends AbstractMysqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customerRepository;

    @Test
    void convertOutdoorEndpointCreatesLinkedJob() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .name("HTTP IT Customer")
                .phone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .address("Mandalay")
                .creditHold(false)
                .blacklisted(false)
                .advanceBalance(BigDecimal.ZERO)
                .build());

        String createBody = objectMapper.writeValueAsString(Map.of(
                "customerId", customer.getId(),
                "bookingDate", LocalDate.now().toString(),
                "complaintNote", "Outdoor visit needed"
        ));

        MvcResult created = mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode createData = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        int bookingId = createData.path("id").asInt();
        assertTrue(bookingId > 0);
        assertEquals("CONFIRMED", createData.path("status").asText());

        mockMvc.perform(post("/api/v1/bookings/{id}/convert-outdoor", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.linkedJobs.length()").value(1))
                .andExpect(jsonPath("$.data.linkedJobs[0].serviceMode").value("OUTDOOR"))
                .andExpect(jsonPath("$.data.linkedJobs[0].jobNo").exists());
    }
}
