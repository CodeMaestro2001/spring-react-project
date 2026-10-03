package com.example.demo.dto;

import com.example.demo.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(UUID productId, String sku, String productName, String unit,
                               String currencyCode, BigDecimal unitPrice, int quantity,
                               int availableQuantity, boolean active, BigDecimal lineTotal) {

    public static CartItemResponse from(Product product, int quantity) {
        return new CartItemResponse(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                product.getCurrencyCode(), product.getPrice(), quantity, product.getStockQuantity(),
                product.isActive(), product.getPrice().multiply(BigDecimal.valueOf(quantity)));
    }
}
