package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product(String sku, String name, String description, String category, String unit,
                   BigDecimal price, String currencyCode) {
        update(sku, name, description, null, category, unit, price, currencyCode);
    }

    public void update(String sku, String name, String description, String imageUrl, String category, String unit,
                       BigDecimal price, String currencyCode) {
        this.sku = sku;
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
        this.category = category.trim();
        this.unit = unit.trim();
        this.price = price;
        this.currencyCode = currencyCode.trim().toUpperCase();
    }

    public void deactivate() {
        active = false;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    @PrePersist
    void setCreatedAt() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = Instant.now();
    }

}
