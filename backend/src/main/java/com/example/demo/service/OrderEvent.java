package com.example.demo.service;

import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable integration event. It deliberately excludes customer email and item
 * descriptions so downstream consumers receive only the data they need.
 */
public record OrderEvent(UUID orderId, UUID accountId, String eventType, OrderStatus status,
                         String currencyCode, BigDecimal totalAmount, Instant occurredAt) {

    public static OrderEvent created(CustomerOrder order) {
        return from(order, "ORDER_CREATED");
    }

    public static OrderEvent statusChanged(CustomerOrder order) {
        return from(order, "ORDER_STATUS_CHANGED");
    }

    private static OrderEvent from(CustomerOrder order, String eventType) {
        return new OrderEvent(order.getId(), order.getAccountId(), eventType, order.getStatus(),
                order.getCurrencyCode(), order.getTotalAmount(), Instant.now());
    }
}
