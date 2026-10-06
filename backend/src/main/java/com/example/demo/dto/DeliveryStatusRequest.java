package com.example.demo.dto;

import com.example.demo.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryStatusRequest(@NotNull DeliveryStatus status) {
}
