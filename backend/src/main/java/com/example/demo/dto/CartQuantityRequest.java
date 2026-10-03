package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CartQuantityRequest(@Min(1) @Max(99) int quantity) {
}
