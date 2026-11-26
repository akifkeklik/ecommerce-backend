package com.ecommerce.application.service;

import com.ecommerce.domain.exception.InvalidDiscountException;
import com.ecommerce.domain.order.OrderItem;
import com.ecommerce.domain.promotion.Discount;
import com.ecommerce.domain.promotion.DiscountType;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.DiscountRepository;
import com.ecommerce.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Checks a discount code against an order and records its use.
 * Every rule that can reject a code is checked here, so an order never shows a code it did not get.
 */
@Service
public class DiscountService {

    /** Only these types reduce the item total; the others need shipping or bundle logic that checkout does not have. */
    private static final Set<DiscountType> CHECKOUT_TYPES = Set.of(DiscountType.FIXED_AMOUNT, DiscountType.PERCENTAGE);

    public record AppliedDiscount(String code, BigDecimal amount) {
    }

    private final DiscountRepository discountRepository;
    private final OrderRepository orderRepository;

    public DiscountService(DiscountRepository discountRepository, OrderRepository orderRepository) {
        this.discountRepository = discountRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Validates the code for this customer and these items, records one use and returns the amount.
     * Must run in the order's transaction: the discount row stays locked until the order is saved.
     */
    @Transactional
    public AppliedDiscount apply(String rawCode, User user, List<OrderItem> items) {
        String code = rawCode.trim();
        Discount discount = discountRepository.findByCodeIgnoreCaseForUpdate(code)
                .orElseThrow(() -> new InvalidDiscountException(code, "code does not exist"));

        if (!discount.isValid()) {
            throw new InvalidDiscountException(code, "code is inactive, expired or fully used");
        }
        if (!CHECKOUT_TYPES.contains(discount.getType())) {
            throw new InvalidDiscountException(code, "this type of discount cannot be used at checkout");
        }
        if (discount.isFirstOrderOnly() && orderRepository.countActiveOrdersByUser(user.getId()) > 0) {
            throw new InvalidDiscountException(code, "code is only valid on a first order");
        }
        Integer perUser = discount.getUsageLimitPerUser();
        if (perUser != null
                && orderRepository.countActiveOrdersByUserWithDiscount(user.getId(), discount.getCode()) >= perUser) {
            throw new InvalidDiscountException(code, "code has already been used the maximum number of times");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal eligible = BigDecimal.ZERO;
        for (OrderItem item : items) {
            subtotal = subtotal.add(item.getTotal());
            if (discount.appliesToProduct(item.getProduct())) {
                eligible = eligible.add(item.getTotal());
            }
        }

        if (eligible.signum() == 0) {
            throw new InvalidDiscountException(code, "code does not apply to any product in this order");
        }
        if (!discount.isApplicableToAmount(subtotal)) {
            throw new InvalidDiscountException(code,
                    "order total is below the minimum of " + discount.getMinimumOrderAmount());
        }

        // Percentages and caps apply to the eligible items only, never to excluded ones.
        BigDecimal amount = discount.calculateDiscount(eligible);
        discount.recordUsage();
        discountRepository.save(discount);
        return new AppliedDiscount(discount.getCode(), amount);
    }

    /** Gives back the use taken by a cancelled order. */
    @Transactional
    public void release(String code) {
        discountRepository.findByCodeIgnoreCaseForUpdate(code).ifPresent(discount -> {
            discount.releaseUsage();
            discountRepository.save(discount);
        });
    }
}
