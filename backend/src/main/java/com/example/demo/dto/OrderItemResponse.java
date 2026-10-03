package com.example.demo.dto;

import com.example.demo.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId, String sku, String productName, String unit,
                                int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductSku(), item.getProductName(),
                item.getUnit(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
