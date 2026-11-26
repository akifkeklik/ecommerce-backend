package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Order entity operations.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    /** Serialises payment attempts on one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :before")
    List<Order> findByStatusAndCreatedAtBefore(@Param("status") OrderStatus status,
            @Param("before") LocalDateTime before);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status = 'DELIVERED'")
    long countDeliveredOrdersByUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.status <> com.ecommerce.domain.order.OrderStatus.CANCELLED")
    long countActiveOrdersByUser(@Param("userId") UUID userId);

    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items i WHERE o.user.id = :userId AND i.product.id = :productId"
            + " AND o.status = com.ecommerce.domain.order.OrderStatus.DELIVERED")
    boolean existsDeliveredOrderWithProduct(@Param("userId") UUID userId, @Param("productId") UUID productId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND UPPER(o.discountCode) = UPPER(:code) AND o.status <> com.ecommerce.domain.order.OrderStatus.CANCELLED")
    long countActiveOrdersByUserWithDiscount(@Param("userId") UUID userId, @Param("code") String code);

    @Query("SELECT o FROM Order o JOIN o.items i WHERE i.product.seller.id = :sellerId")
    Page<Order> findOrdersBySeller(@Param("sellerId") UUID sellerId, Pageable pageable);
}
