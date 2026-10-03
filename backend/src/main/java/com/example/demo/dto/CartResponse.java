package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartItemResponse> items, String currencyCode,
                           int totalQuantity, BigDecimal subtotal) {

    public static CartResponse from(List<CartItemResponse> items) {
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        String currencyCode = items.isEmpty() ? null : items.getFirst().currencyCode();
        return new CartResponse(items, currencyCode, totalQuantity, subtotal);
    }
}
