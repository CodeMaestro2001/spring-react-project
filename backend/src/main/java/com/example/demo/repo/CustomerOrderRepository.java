package com.example.demo.repo;

import com.example.demo.model.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    Optional<CustomerOrder> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);

    Optional<CustomerOrder> findByIdAndAccountId(UUID id, UUID accountId);

    List<CustomerOrder> findTop50ByAccountIdOrderByPlacedAtDesc(UUID accountId);

    List<CustomerOrder> findTop100ByOrderByPlacedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);
}
