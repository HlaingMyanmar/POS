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
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
@WithMockUser(username = "it-admin", authorities = {
        "CAN_ACCESS_PRODUCT_READ",
        "CAN_ACCESS_PRODUCT_UPDATE",
        "CAN_ACCESS_PRODUCT_SERIAL_READ"
})
class ProductPhotosHttpIT extends AbstractMysqlIntegrationTest {

    private static final String TINY_JPEG =
            "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwAA8A/9k=";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private UnitRepository unitRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSerialRepository productSerialRepository;

    @Test
    void putThreePhotosAndListAvailableSerials() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = new Category();
        category.setName("RT Cat " + suffix);
        category = categoryRepository.save(category);

        Brand brand = new Brand();
        brand.setName("RT Brand " + suffix);
        brand = brandRepository.save(brand);

        Unit unit = unitRepository.findAll().stream().findFirst().orElseGet(() -> {
            Unit u = new Unit();
            u.setUnitName("RT Unit " + suffix);
            u.setDescription("runtime unit " + suffix);
            return unitRepository.save(u);
        });

        Product product = productRepository.save(Product.builder()
                .productCode("RT" + suffix)
                .name("Runtime Serial Product " + suffix)
                .productType(ProductType.New)
                .sellingPrice(BigDecimal.TEN)
                .costPrice(BigDecimal.ONE)
                .category(category)
                .brand(brand)
                .unit(unit)
                .hasSerial(true)
                .stockQty(1)
                .build());

        productSerialRepository.save(ProductSerial.builder()
                .serialNumber("SN-" + suffix)
                .status(SerialStatus.Available)
                .product(product)
                .build());

        String photosBody = objectMapper.writeValueAsString(List.of(
                Map.of("slot", 1, "dataUrl", TINY_JPEG, "fileName", "a.jpg"),
                Map.of("slot", 2, "dataUrl", TINY_JPEG, "fileName", "b.jpg"),
                Map.of("slot", 3, "dataUrl", TINY_JPEG, "fileName", "c.jpg")
        ));

        mockMvc.perform(put("/api/v1/products/{id}/photos", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(photosBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult productRes = mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.photos.length()").value(3))
                .andReturn();

        JsonNode photos = objectMapper.readTree(productRes.getResponse().getContentAsString())
                .path("data").path("photos");
        assertEquals(3, photos.size());
        for (JsonNode photo : photos) {
            int slot = photo.path("slot").asInt();
            assertTrue(slot >= 1 && slot <= 3);
            assertFalse(photo.path("imagePath").asText("").isBlank());
            assertFalse(photo.path("thumbnailPath").asText("").isBlank());
        }

        MvcResult serialRes = mockMvc.perform(get("/api/v1/product-serials/by-product/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode serial = objectMapper.readTree(serialRes.getResponse().getContentAsString()).path("data").get(0);
        assertEquals("Available", serial.path("status").asText());
        assertEquals("SN-" + suffix, serial.path("serialNumber").asText());
    }
}
