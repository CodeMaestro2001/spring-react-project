package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartAddRequest(@NotNull UUID productId, @Min(1) @Max(99) int quantity) {
}
