package com.example.demo.controller;

import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.CheckoutRequest;
import com.example.demo.security.AccountPrincipal;
import com.example.demo.service.OrderService;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return orderService.customerOrders(principal.id());
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID orderId) {
        return orderService.customerOrder(principal.id(), orderId);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestHeader("Idempotency-Key") @Size(max = 100) String idempotencyKey,
                                  @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(principal.id(), idempotencyKey, request);
    }
}
