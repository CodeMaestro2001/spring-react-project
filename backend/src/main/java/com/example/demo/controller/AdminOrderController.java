package com.example.demo.controller;

import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.OrderStatusRequest;
import com.example.demo.dto.PaymentStatusRequest;
import com.example.demo.dto.DeliveryStatusRequest;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.recentOrders();
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateStatus(@PathVariable UUID orderId,
                                      @Valid @RequestBody OrderStatusRequest request) {
        return orderService.updateStatus(orderId, request.status());
    }

    @PatchMapping("/{orderId}/payment-status")
    public OrderResponse updatePaymentStatus(@PathVariable UUID orderId,
                                             @Valid @RequestBody PaymentStatusRequest request) {
        return orderService.updatePaymentStatus(orderId, request.status());
    }

    @PatchMapping("/{orderId}/delivery-status")
    public OrderResponse updateDeliveryStatus(@PathVariable UUID orderId,
                                              @Valid @RequestBody DeliveryStatusRequest request) {
        return orderService.updateDeliveryStatus(orderId, request.status());
    }
}
