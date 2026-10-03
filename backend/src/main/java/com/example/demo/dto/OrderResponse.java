package com.example.demo.dto;

import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, UUID accountId, String customerEmail, OrderStatus status,
                            String currencyCode, BigDecimal totalAmount, Instant placedAt,
                            List<OrderItemResponse> items) {

    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(order.getId(), order.getAccountId(), order.getCustomerEmail(),
                order.getStatus(), order.getCurrencyCode(), order.getTotalAmount(), order.getPlacedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
