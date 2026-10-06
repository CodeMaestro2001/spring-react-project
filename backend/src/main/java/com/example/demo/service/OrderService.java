package com.example.demo.service;

import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.CheckoutRequest;
import com.example.demo.model.DeliveryStatus;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.Account;
import com.example.demo.model.CartItem;
import com.example.demo.model.CustomerOrder;
import com.example.demo.model.OrderItem;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Product;
import com.example.demo.model.ShoppingCart;
import com.example.demo.repo.AccountRepository;
import com.example.demo.repo.CartItemRepository;
import com.example.demo.repo.CustomerOrderRepository;
import com.example.demo.repo.ProductRepository;
import com.example.demo.repo.ShoppingCartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final AccountRepository accountRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OrderService(AccountRepository accountRepository, ShoppingCartRepository shoppingCartRepository,
                        CartItemRepository cartItemRepository, ProductRepository productRepository,
                        CustomerOrderRepository customerOrderRepository,
                        ApplicationEventPublisher applicationEventPublisher) {
        this.accountRepository = accountRepository;
        this.shoppingCartRepository = shoppingCartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public OrderResponse checkout(UUID accountId, String idempotencyKey, CheckoutRequest request) {
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedKey.isBlank() || normalizedKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required.");
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        var existingOrder = customerOrderRepository.findByAccountIdAndIdempotencyKey(accountId, normalizedKey);
        if (existingOrder.isPresent()) {
            return OrderResponse.from(existingOrder.get());
        }

        ShoppingCart cart = shoppingCartRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty."));
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByProductId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty.");
        }

        List<UUID> productIds = cartItems.stream().map(CartItem::getProductId).toList();
        List<Product> lockedProducts = productRepository.findAllByIdInForUpdate(productIds);
        Map<UUID, Product> products = lockedProducts.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != cartItems.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product in your cart is unavailable.");
        }

        Set<String> currencies = lockedProducts.stream().map(Product::getCurrencyCode).collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your cart contains different currencies. Remove items so checkout uses one currency.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = products.get(item.getProductId());
            if (!product.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        product.getName() + " is no longer available. Remove it from your cart.");
            }
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock for " + product.getName() + ". Available: " + product.getStockQuantity() + ".");
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        String currencyCode = currencies.iterator().next();
        CustomerOrder order = new CustomerOrder(account.getId(), account.getEmail(), normalizedKey,
                currencyCode, total, request.paymentMethod(), request.deliveryRecipient(), request.deliveryAddress(),
                request.deliveryPhone(), request.deliveryNote());
        for (CartItem item : cartItems) {
            Product product = products.get(item.getProductId());
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            order.addItem(new OrderItem(product.getId(), product.getSku(), product.getName(), product.getUnit(),
                    item.getQuantity(), product.getPrice(), lineTotal));
        }

        customerOrderRepository.saveAndFlush(order);
        cartItemRepository.deleteAllByCartId(cart.getId());
        applicationEventPublisher.publishEvent(OrderEvent.created(order));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> customerOrders(UUID accountId) {
        return customerOrderRepository.findTop50ByAccountIdOrderByPlacedAtDesc(accountId).stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse customerOrder(UUID accountId, UUID orderId) {
        return customerOrderRepository.findByIdAndAccountId(orderId, accountId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> recentOrders() {
        return customerOrderRepository.findTop100ByOrderByPlacedAtDesc().stream()
                .map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse updateStatus(UUID orderId, OrderStatus nextStatus) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == nextStatus) {
            return OrderResponse.from(order);
        }

        boolean cancel = nextStatus == OrderStatus.CANCELLED
                && (currentStatus == OrderStatus.PLACED || currentStatus == OrderStatus.PROCESSING);
        boolean advance = (currentStatus == OrderStatus.PLACED && nextStatus == OrderStatus.PROCESSING)
                || (currentStatus == OrderStatus.PROCESSING && nextStatus == OrderStatus.COMPLETED);
        if (!cancel && !advance) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid order transition from " + currentStatus + " to " + nextStatus + ".");
        }

        if (cancel) {
            List<UUID> productIds = order.getItems().stream().map(OrderItem::getProductId).distinct().sorted().toList();
            Map<UUID, Product> products = productRepository.findAllByIdInForUpdate(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            for (OrderItem item : order.getItems()) {
                Product product = products.get(item.getProductId());
                if (product == null || (long) product.getStockQuantity() + item.getQuantity() > 10000000) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot restore stock for this order. Check product inventory.");
                }
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
        }

        order.setStatus(nextStatus);
        if (cancel) {
            order.setDeliveryStatus(DeliveryStatus.CANCELLED);
        }
        applicationEventPublisher.publishEvent(OrderEvent.statusChanged(order));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updatePaymentStatus(UUID orderId, PaymentStatus nextStatus) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        PaymentStatus currentStatus = order.getPaymentStatus();
        if (currentStatus == nextStatus) return OrderResponse.from(order);
        boolean valid = (currentStatus == PaymentStatus.PENDING
                && (nextStatus == PaymentStatus.PAID || nextStatus == PaymentStatus.FAILED))
                || (currentStatus == PaymentStatus.PAID && nextStatus == PaymentStatus.REFUNDED);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid payment transition from " + currentStatus + " to " + nextStatus + ".");
        }
        order.setPaymentStatus(nextStatus);
        applicationEventPublisher.publishEvent(OrderEvent.statusChanged(order));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(UUID orderId, DeliveryStatus nextStatus) {
        CustomerOrder order = customerOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        DeliveryStatus currentStatus = order.getDeliveryStatus();
        if (currentStatus == nextStatus) return OrderResponse.from(order);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancelled order cannot be delivered.");
        }
        boolean valid = (currentStatus == DeliveryStatus.PENDING && nextStatus == DeliveryStatus.PREPARING)
                || (currentStatus == DeliveryStatus.PREPARING && nextStatus == DeliveryStatus.DISPATCHED)
                || (currentStatus == DeliveryStatus.DISPATCHED && nextStatus == DeliveryStatus.DELIVERED);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid delivery transition from " + currentStatus + " to " + nextStatus + ".");
        }
        order.setDeliveryStatus(nextStatus);
        applicationEventPublisher.publishEvent(OrderEvent.statusChanged(order));
        return OrderResponse.from(order);
    }
}
