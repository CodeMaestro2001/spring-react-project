package com.example.demo.service;

import com.example.demo.dto.CartItemResponse;
import com.example.demo.dto.CartResponse;
import com.example.demo.model.CartItem;
import com.example.demo.model.Product;
import com.example.demo.model.ShoppingCart;
import com.example.demo.repo.AccountRepository;
import com.example.demo.repo.CartItemRepository;
import com.example.demo.repo.ProductRepository;
import com.example.demo.repo.ShoppingCartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final AccountRepository accountRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(AccountRepository accountRepository, ShoppingCartRepository shoppingCartRepository,
                       CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.accountRepository = accountRepository;
        this.shoppingCartRepository = shoppingCartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse get(UUID accountId) {
        return shoppingCartRepository.findByAccountId(accountId)
                .map(this::cartResponse)
                .orElseGet(() -> CartResponse.from(List.of()));
    }

    @Transactional
    public CartResponse add(UUID accountId, UUID productId, int quantity) {
        lockAccount(accountId);
        ShoppingCart cart = getOrCreateCart(accountId);
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This product is no longer available.");
        }

        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByProductId(cart.getId());
        ensureCartCurrency(cartItems, product);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        if (newQuantity > 99) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cart line cannot exceed 99 units.");
        }
        ensureStock(product, newQuantity);
        if (item == null) {
            cartItemRepository.save(new CartItem(cart.getId(), productId, newQuantity));
        } else {
            item.setQuantity(newQuantity);
        }
        return cartResponse(cart);
    }

    @Transactional
    public CartResponse setQuantity(UUID accountId, UUID productId, int quantity) {
        lockAccount(accountId);
        ShoppingCart cart = findCart(accountId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found."));
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        ensureStock(product, quantity);
        item.setQuantity(quantity);
        return cartResponse(cart);
    }

    @Transactional
    public CartResponse remove(UUID accountId, UUID productId) {
        lockAccount(accountId);
        ShoppingCart cart = findCart(accountId);
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        return cartResponse(cart);
    }

    private void lockAccount(UUID accountId) {
        accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private ShoppingCart getOrCreateCart(UUID accountId) {
        return shoppingCartRepository.findByAccountId(accountId)
                .orElseGet(() -> shoppingCartRepository.saveAndFlush(new ShoppingCart(accountId)));
    }

    private ShoppingCart findCart(UUID accountId) {
        return shoppingCartRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found."));
    }

    private CartResponse cartResponse(ShoppingCart cart) {
        List<CartItem> items = cartItemRepository.findAllByCartIdOrderByProductId(cart.getId());
        if (items.isEmpty()) {
            return CartResponse.from(List.of());
        }
        List<UUID> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<CartItemResponse> responses = items.stream().map(item -> {
            Product product = products.get(item.getProductId());
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A product in this cart is no longer available.");
            }
            return CartItemResponse.from(product, item.getQuantity());
        }).toList();
        return CartResponse.from(responses);
    }

    private void ensureCartCurrency(List<CartItem> items, Product newProduct) {
        if (items.isEmpty()) {
            return;
        }
        List<UUID> productIds = items.stream().map(CartItem::getProductId).toList();
        boolean currencyMatches = productRepository.findAllById(productIds).stream()
                .allMatch(product -> product.getCurrencyCode().equals(newProduct.getCurrencyCode()));
        if (!currencyMatches) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A cart can contain products in one currency only. Remove the other items first.");
        }
    }

    private void ensureStock(Product product, int quantity) {
        if (quantity > product.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only " + product.getStockQuantity() + " " + product.getUnit() + " available in stock.");
        }
    }
}
