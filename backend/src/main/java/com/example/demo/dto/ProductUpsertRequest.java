package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

public record ProductUpsertRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String sku,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) @URL(protocol = "https") String imageUrl,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 32) String unit,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode) {
}
