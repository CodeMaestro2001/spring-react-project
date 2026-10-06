package com.example.demo.dto;

import com.example.demo.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank @Size(max = 120) String deliveryRecipient,
        @NotBlank @Size(max = 500) String deliveryAddress,
        @Size(max = 30) String deliveryPhone,
        @Size(max = 500) String deliveryNote,
        @NotNull PaymentMethod paymentMethod) {
}
