package com.example.demo.controller;

import com.example.demo.dto.CartAddRequest;
import com.example.demo.dto.CartQuantityRequest;
import com.example.demo.dto.CartResponse;
import com.example.demo.security.AccountPrincipal;
import com.example.demo.service.CartService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AccountPrincipal principal) {
        return cartService.get(principal.id());
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal AccountPrincipal principal,
                            @Valid @RequestBody CartAddRequest request) {
        return cartService.add(principal.id(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse setQuantity(@AuthenticationPrincipal AccountPrincipal principal,
                                    @PathVariable UUID productId,
                                    @Valid @RequestBody CartQuantityRequest request) {
        return cartService.setQuantity(principal.id(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@AuthenticationPrincipal AccountPrincipal principal,
                               @PathVariable UUID productId) {
        return cartService.remove(principal.id(), productId);
    }
}
