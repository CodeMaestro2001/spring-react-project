package com.example.demo.dto;

import com.example.demo.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, String description, String category,
                              String unit, BigDecimal price, String currencyCode, boolean active,
                              int stockQuantity) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getCategory(), product.getUnit(), product.getPrice(),
                product.getCurrencyCode(), product.isActive(), product.getStockQuantity());
    }
}
