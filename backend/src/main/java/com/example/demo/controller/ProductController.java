package com.example.demo.controller;

import com.example.demo.dto.ProductPageResponse;
import com.example.demo.dto.ProductResponse;
import com.example.demo.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ProductPageResponse browse(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        return productService.browse(q, category, page, size);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productService.categories();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.getActiveProduct(id);
    }
}
