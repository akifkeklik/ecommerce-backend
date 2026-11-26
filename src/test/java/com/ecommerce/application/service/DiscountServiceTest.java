package com.ecommerce.application.service;

import com.ecommerce.application.service.DiscountService.AppliedDiscount;
import com.ecommerce.domain.exception.InvalidDiscountException;
import com.ecommerce.domain.order.OrderItem;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.promotion.Discount;
import com.ecommerce.domain.promotion.DiscountType;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.DiscountRepository;
import com.ecommerce.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private OrderRepository orderRepository;

    private DiscountService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new DiscountService(discountRepository, orderRepository);
        user = new User();
        user.setId(UUID.randomUUID());
        lenient().when(discountRepository.save(any(Discount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Discount discount(DiscountType type, String value) {
        Discount d = new Discount();
        d.setCode("SAVE10");
        d.setType(type);
        d.setValue(new BigDecimal(value));
        return d;
    }

    private static OrderItem item(String price, int quantity) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Item " + price);
        product.setSku("SKU-" + UUID.randomUUID());
        product.setPrice(new BigDecimal(price));
        return OrderItem.fromProduct(product, quantity);
    }

    private void stored(Discount d) {
        when(discountRepository.findByCodeIgnoreCaseForUpdate("save10")).thenReturn(Optional.of(d));
    }

    @Test
    @DisplayName("A percentage code reduces the total, is matched case-insensitively and counts one use")
    void percentage_appliesAndRecordsUse() {
        Discount d = discount(DiscountType.PERCENTAGE, "10");
        stored(d);

        AppliedDiscount applied = service.apply(" save10 ", user, List.of(item("50.00", 2), item("19.99", 1)));

        assertThat(applied.code()).isEqualTo("SAVE10");
        assertThat(applied.amount()).isEqualByComparingTo("12.00");
        assertThat(d.getUsageCount()).isEqualTo(1);
        verify(discountRepository).save(d);
    }

    @Test
    @DisplayName("A fixed amount is capped by the maximum and never exceeds the eligible items")
    void fixedAmount_isCapped() {
        Discount d = discount(DiscountType.FIXED_AMOUNT, "80");
        d.setMaximumDiscountAmount(new BigDecimal("30"));
        stored(d);
        assertThat(service.apply("save10", user, List.of(item("100.00", 1))).amount()).isEqualByComparingTo("30");

        Discount big = discount(DiscountType.FIXED_AMOUNT, "500");
        stored(big);
        assertThat(service.apply("save10", user, List.of(item("40.00", 1))).amount()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("An excluded product does not count towards a percentage discount")
    void excludedProduct_isNotDiscounted() {
        OrderItem excluded = item("100.00", 1);
        OrderItem normal = item("50.00", 1);
        Discount d = discount(DiscountType.PERCENTAGE, "20");
        d.setExcludedProductIds(Set.of(excluded.getProduct().getId().toString()));
        stored(d);

        assertThat(service.apply("save10", user, List.of(excluded, normal)).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Unknown, inactive, expired and used-up codes are rejected without recording a use")
    void invalidCodes_areRejected() {
        when(discountRepository.findByCodeIgnoreCaseForUpdate("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply("nope", user, List.of(item("10.00", 1))))
                .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("does not exist");

        Discount inactive = discount(DiscountType.PERCENTAGE, "10");
        inactive.deactivate();
        Discount expired = discount(DiscountType.PERCENTAGE, "10");
        expired.setEndDate(LocalDateTime.now().minusDays(1));
        Discount usedUp = discount(DiscountType.PERCENTAGE, "10");
        usedUp.setUsageLimit(5);
        usedUp.setUsageCount(5);

        for (Discount d : List.of(inactive, expired, usedUp)) {
            stored(d);
            assertThatThrownBy(() -> service.apply("save10", user, List.of(item("10.00", 1))))
                    .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("inactive, expired or fully used");
        }
        verify(discountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Types checkout cannot honour are rejected instead of silently giving nothing")
    void unsupportedType_isRejected() {
        stored(discount(DiscountType.FREE_SHIPPING, "5"));
        assertThatThrownBy(() -> service.apply("save10", user, List.of(item("10.00", 1))))
                .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("cannot be used at checkout");
    }

    @Test
    @DisplayName("Minimum order amount is enforced on the whole order")
    void minimumAmount_isEnforced() {
        Discount d = discount(DiscountType.PERCENTAGE, "10");
        d.setMinimumOrderAmount(new BigDecimal("100.00"));
        stored(d);

        assertThatThrownBy(() -> service.apply("save10", user, List.of(item("99.99", 1))))
                .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("minimum of 100.00");
        assertThat(service.apply("save10", user, List.of(item("50.00", 2))).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("First-order codes and per-customer limits look at the customer's earlier, non-cancelled orders")
    void perCustomerRules_areEnforced() {
        Discount firstOrder = discount(DiscountType.PERCENTAGE, "10");
        firstOrder.setFirstOrderOnly(true);
        stored(firstOrder);
        when(orderRepository.countActiveOrdersByUser(user.getId())).thenReturn(1L);
        assertThatThrownBy(() -> service.apply("save10", user, List.of(item("10.00", 1))))
                .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("first order");

        Discount oncePerCustomer = discount(DiscountType.PERCENTAGE, "10");
        oncePerCustomer.setUsageLimitPerUser(1);
        stored(oncePerCustomer);
        when(orderRepository.countActiveOrdersByUserWithDiscount(user.getId(), "SAVE10")).thenReturn(1L);
        assertThatThrownBy(() -> service.apply("save10", user, List.of(item("10.00", 1))))
                .isInstanceOf(InvalidDiscountException.class).hasMessageContaining("maximum number of times");
    }

    @Test
    @DisplayName("Cancelling gives the use back, and never below zero")
    void release_givesUseBack() {
        Discount d = discount(DiscountType.PERCENTAGE, "10");
        d.setUsageCount(1);
        stored(d);

        service.release("save10");
        service.release("save10");

        assertThat(d.getUsageCount()).isZero();
    }
}
