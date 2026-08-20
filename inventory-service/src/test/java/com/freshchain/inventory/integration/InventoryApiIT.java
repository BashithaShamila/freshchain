package com.freshchain.inventory.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.inventory.api.dto.ReceiveLotRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Contract and authorisation rules for the inventory API. */
// Spring Boot turns metrics and tracing off inside @SpringBootTest; without this
// the Prometheus endpoint would simply not be registered, and the test below
// would pass or fail for reasons that have nothing to do with authorisation.
@AutoConfigureObservability
class InventoryApiIT extends AbstractWebIntegrationTest {

    private static final String SKU = "CHK-BRST-5LB";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("receiving stock without a token is rejected outright")
    void anonymousCallerCannotReceiveStock() throws Exception {
        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(40)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a customer cannot receive stock — that is an admin operation")
    void customerCannotReceiveStock() throws Exception {
        mockMvc.perform(post("/api/v1/lots")
                        .with(as("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(40)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can receive stock into a new lot")
    void adminCanReceiveStock() throws Exception {
        mockMvc.perform(post("/api/v1/lots")
                        .with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(40)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(SKU))
                .andExpect(jsonPath("$.qtyOnHand").value(40))
                .andExpect(jsonPath("$.qtyReserved").value(0))
                .andExpect(jsonPath("$.available").value(40));
    }

    @Test
    @DisplayName("availability reports the lot breakdown, not just a total")
    void availabilityShowsPerLotDetail() throws Exception {
        mockMvc.perform(post("/api/v1/lots").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body(25, 3)));
        mockMvc.perform(post("/api/v1/lots").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body(100, 30)));

        mockMvc.perform(get("/api/v1/inventory/{sku}/availability", SKU)
                        .param("warehouseId", TestFixtures.WAREHOUSE)
                        .with(as("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(125))
                .andExpect(jsonPath("$.lots.length()").value(2))
                // Sorted nearest-expiry first, which is the order stock will go out in.
                .andExpect(jsonPath("$.lots[0].qtyOnHand").value(25))
                .andExpect(jsonPath("$.nearestExpiry").value(LocalDate.now().plusDays(3).toString()));
    }

    @Test
    @DisplayName("stock that is already unsellable cannot be received")
    void receivingExpiredStockIsRejected() throws Exception {
        String expired = objectMapper.writeValueAsString(new ReceiveLotRequest(
                SKU, TestFixtures.WAREHOUSE, 40, LocalDate.now().minusDays(1)));

        mockMvc.perform(post("/api/v1/lots").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(expired))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("an unknown SKU is a 404 with a usable message")
    void unknownSkuIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/{sku}/availability", "NOPE-000")
                        .with(as("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unknown sku NOPE-000"));
    }

    @Test
    @DisplayName("a reservation that does not exist is a 404, not an empty 200")
    void unknownReservationIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/{orderId}", UUID.randomUUID())
                        .with(as("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("health and metrics stay open so the platform can scrape them")
    void actuatorEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- fixtures --

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(builder -> builder.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String body(int qty) throws Exception {
        return body(qty, 7);
    }

    private String body(int qty, int daysToExpiry) throws Exception {
        return objectMapper.writeValueAsString(new ReceiveLotRequest(
                SKU, TestFixtures.WAREHOUSE, qty, LocalDate.now().plusDays(daysToExpiry)));
    }
}
