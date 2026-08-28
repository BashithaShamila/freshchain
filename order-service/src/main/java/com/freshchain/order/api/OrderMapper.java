package com.freshchain.order.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.EventEnvelope;
import com.freshchain.order.api.dto.OrderResponse;
import com.freshchain.order.domain.Order;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper {

    private static final TypeReference<List<EventEnvelope.InventoryReserved.Substitution>> ADVICE_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public OrderResponse toResponse(Order order) {
        return OrderResponse.from(order, readAdvice(order));
    }

    private List<EventEnvelope.InventoryReserved.Substitution> readAdvice(Order order) {
        String stored = order.getSubstitutionAdvice();
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stored, ADVICE_TYPE);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Advisory data from another service. If its shape has drifted, drop
            // it rather than fail a read of the order itself.
            log.warn("could not read substitution advice on order {}", order.getOrderId(), e);
            return List.of();
        }
    }
}
