package com.example.demo.repo;

import com.example.demo.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findAllByCartIdOrderByProductId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    void deleteAllByCartId(UUID cartId);

    void deleteByCartIdAndProductId(UUID cartId, UUID productId);
}
