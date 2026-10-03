package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminStockRequest(@Min(0) @Max(10000000) int quantity) {
}
