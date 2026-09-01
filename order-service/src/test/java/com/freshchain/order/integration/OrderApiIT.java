package com.freshchain.order.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.order.api.dto.PlaceOrderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Contract and, above all, ownership rules for the order API. */
class OrderApiIT extends AbstractWebIntegrationTest {

    private static final String SKU = "CHK-BRST-5LB";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("placement answers 202, because allocation has not happened yet")
    void placementIsAcceptedNotCreated() throws Exception {
        UUID customer = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders")
                        .with(as(customer, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(40)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.order.status").value("PENDING_ALLOCATION"))
                .andExpect(jsonPath("$.order.customerId").value(customer.toString()))
                .andExpect(jsonPath("$.order.lines[0].qtyRequested").value(40))
                .andExpect(jsonPath("$.order.lines[0].qtyAllocated").value(0));
    }

    @Test
    @DisplayName("a customer reading another customer's order gets 403, not 404")
    void customersCannotReadEachOthersOrders() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID orderId = placeOrderAs(alice);

        // 403 rather than 404 on purpose: Bob is authenticated and the order does
        // exist. Answering 404 would leak nothing, but it would also lie.
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).with(as(bob, "CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).with(as(alice, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    @DisplayName("an admin may read any order")
    void adminsAreExemptFromTheOwnershipCheck() throws Exception {
        UUID orderId = placeOrderAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .with(as(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a customer cannot confirm somebody else's order either")
    void ownershipIsEnforcedOnStateChangesToo() throws Exception {
        UUID orderId = placeOrderAs(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders/{orderId}/confirm", orderId)
                        .with(as(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void placingAnOrderWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(40)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a warehouse operator has no business placing orders")
    void wrongRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(as(UUID.randomUUID(), "WAREHOUSE_OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(40)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmptyOrderIsRejectedWithFieldLevelDetail() throws Exception {
        String empty = objectMapper.writeValueAsString(
                new PlaceOrderRequest("WH-COL-01", true, List.of()));

        mockMvc.perform(post("/api/v1/orders")
                        .with(as(UUID.randomUUID(), "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void anUnpricedSkuIsRejected() throws Exception {
        String unknown = objectMapper.writeValueAsString(new PlaceOrderRequest(
                "WH-COL-01", true, List.of(new PlaceOrderRequest.Line("NOPE-000", 5))));

        mockMvc.perform(post("/api/v1/orders")
                        .with(as(UUID.randomUUID(), "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no price on file for sku NOPE-000"));
    }

    @Test
    void aCustomerOnlySeesTheirOwnOrdersInTheListing() throws Exception {
        UUID alice = UUID.randomUUID();
        placeOrderAs(alice);
        placeOrderAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/orders").with(as(alice, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerId").value(alice.toString()));
    }

    // ---------------------------------------------------------------- fixtures --

    private UUID placeOrderAs(UUID customer) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .with(as(customer, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeOrderBody(40)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return UUID.fromString(body.get("order").get("orderId").asText());
    }

    private String placeOrderBody(int qty) throws Exception {
        return objectMapper.writeValueAsString(new PlaceOrderRequest(
                "WH-COL-01", true, List.of(new PlaceOrderRequest.Line(SKU, qty))));
    }

    private static RequestPostProcessor as(UUID userId, String role) {
        return jwt().jwt(builder -> builder.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
