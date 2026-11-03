package com.ecommerce.domain.promotion;

import com.ecommerce.domain.common.BaseEntity;
import com.ecommerce.domain.product.Category;
import com.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Discount entity for coupon codes and promotions.
 */
@Entity
@Table(name = "discounts", indexes = {
        @Index(name = "idx_discount_code", columnList = "code", unique = true),
        @Index(name = "idx_discount_active", columnList = "is_active"),
        @Index(name = "idx_discount_dates", columnList = "start_date, end_date")
})
public class Discount extends BaseEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Size(max = 255)
    @Column(name = "description")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private DiscountType type;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "\"value\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @DecimalMin(value = "0.00")
    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    @DecimalMin(value = "0.00")
    @Column(name = "maximum_discount_amount", precision = 12, scale = 2)
    private BigDecimal maximumDiscountAmount;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Min(0)
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Min(0)
    @Column(name = "usage_limit_per_user")
    private Integer usageLimitPerUser;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_first_order_only")
    private boolean firstOrderOnly = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "discount_products", joinColumns = @JoinColumn(name = "discount_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> applicableProducts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "discount_categories", joinColumns = @JoinColumn(name = "discount_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> applicableCategories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "discount_excluded_products", joinColumns = @JoinColumn(name = "discount_id"))
    @Column(name = "product_id", length = 36)
    private Set<String> excludedProductIds = new HashSet<>();

    // ================== Constructors ==================

    public Discount() {
        this.usageCount = 0;
        this.active = true;
        this.firstOrderOnly = false;
        this.applicableProducts = new HashSet<>();
        this.applicableCategories = new HashSet<>();
        this.excludedProductIds = new HashSet<>();
    }

    public Discount(String code, String description, DiscountType type, BigDecimal value,
            BigDecimal minimumOrderAmount, BigDecimal maximumDiscountAmount,
            LocalDateTime startDate, LocalDateTime endDate, Integer usageLimit,
            Integer usageCount, Integer usageLimitPerUser, boolean active,
            boolean firstOrderOnly, Set<Product> applicableProducts,
            Set<Category> applicableCategories, Set<String> excludedProductIds) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.value = value;
        this.minimumOrderAmount = minimumOrderAmount;
        this.maximumDiscountAmount = maximumDiscountAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.usageLimit = usageLimit;
        this.usageCount = usageCount != null ? usageCount : 0;
        this.usageLimitPerUser = usageLimitPerUser;
        this.active = active;
        this.firstOrderOnly = firstOrderOnly;
        this.applicableProducts = applicableProducts != null ? applicableProducts : new HashSet<>();
        this.applicableCategories = applicableCategories != null ? applicableCategories : new HashSet<>();
        this.excludedProductIds = excludedProductIds != null ? excludedProductIds : new HashSet<>();
    }

    // ================== Getters and Setters ==================

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getType() {
        return type;
    }

    public void setType(DiscountType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) {
        this.minimumOrderAmount = minimumOrderAmount;
    }

    public BigDecimal getMaximumDiscountAmount() {
        return maximumDiscountAmount;
    }

    public void setMaximumDiscountAmount(BigDecimal maximumDiscountAmount) {
        this.maximumDiscountAmount = maximumDiscountAmount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public Integer getUsageLimitPerUser() {
        return usageLimitPerUser;
    }

    public void setUsageLimitPerUser(Integer usageLimitPerUser) {
        this.usageLimitPerUser = usageLimitPerUser;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFirstOrderOnly() {
        return firstOrderOnly;
    }

    public void setFirstOrderOnly(boolean firstOrderOnly) {
        this.firstOrderOnly = firstOrderOnly;
    }

    public Set<Product> getApplicableProducts() {
        return applicableProducts;
    }

    public void setApplicableProducts(Set<Product> applicableProducts) {
        this.applicableProducts = applicableProducts;
    }

    public Set<Category> getApplicableCategories() {
        return applicableCategories;
    }

    public void setApplicableCategories(Set<Category> applicableCategories) {
        this.applicableCategories = applicableCategories;
    }

    public Set<String> getExcludedProductIds() {
        return excludedProductIds;
    }

    public void setExcludedProductIds(Set<String> excludedProductIds) {
        this.excludedProductIds = excludedProductIds;
    }

    // ================== Business Methods ==================

    /**
     * Checks if the discount is currently valid.
     */
    public boolean isValid() {
        if (!active)
            return false;

        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate))
            return false;
        if (endDate != null && now.isAfter(endDate))
            return false;
        if (usageLimit != null && usageCount >= usageLimit)
            return false;

        return true;
    }

    /**
     * Checks if the discount can be applied to the given order amount.
     */
    public boolean isApplicableToAmount(BigDecimal orderAmount) {
        if (minimumOrderAmount == null)
            return true;
        return orderAmount.compareTo(minimumOrderAmount) >= 0;
    }

    /**
     * Calculates the discount amount for a given order total.
     */
    public BigDecimal calculateDiscount(BigDecimal orderTotal) {
        if (!isValid() || !isApplicableToAmount(orderTotal)) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        switch (type) {
            case FIXED_AMOUNT:
                discount = value;
                break;
            case PERCENTAGE:
                discount = orderTotal.multiply(value).divide(BigDecimal.valueOf(100), 2,
                        java.math.RoundingMode.HALF_UP);
                break;
            case FREE_SHIPPING:
                return value; // Return the shipping cost as discount
            default:
                discount = BigDecimal.ZERO;
        }

        // Apply maximum discount cap
        if (maximumDiscountAmount != null && discount.compareTo(maximumDiscountAmount) > 0) {
            discount = maximumDiscountAmount;
        }

        // Discount cannot exceed order total
        if (discount.compareTo(orderTotal) > 0) {
            discount = orderTotal;
        }

        return discount;
    }

    /**
     * Records usage of the discount.
     */
    public void recordUsage() {
        this.usageCount++;
    }

    /**
     * Checks if the discount applies to a specific product.
     */
    public boolean appliesToProduct(Product product) {
        // If no specific products/categories are set, applies to all
        if (applicableProducts.isEmpty() && applicableCategories.isEmpty()) {
            return !excludedProductIds.contains(product.getId().toString());
        }

        // Check if product is in applicable products
        if (applicableProducts.contains(product)) {
            return true;
        }

        // Check if product's category is in applicable categories
        if (product.getCategory() != null && applicableCategories.contains(product.getCategory())) {
            return true;
        }

        return false;
    }

    /**
     * Deactivates the discount.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Extends the discount end date.
     */
    public void extendEndDate(LocalDateTime newEndDate) {
        if (newEndDate.isAfter(LocalDateTime.now())) {
            this.endDate = newEndDate;
        }
    }
}
