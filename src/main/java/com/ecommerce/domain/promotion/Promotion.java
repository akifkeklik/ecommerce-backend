package com.ecommerce.domain.promotion;

import com.ecommerce.domain.common.BaseEntity;
import com.ecommerce.domain.product.Category;
import com.ecommerce.domain.product.Product;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Promotion entity for sales and promotional campaigns.
 */
@Entity
@Table(name = "promotions", indexes = {
        @Index(name = "idx_promotion_active", columnList = "is_active"),
        @Index(name = "idx_promotion_dates", columnList = "start_date, end_date")
})
public class Promotion extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DiscountType type;

    @Column(name = "\"value\"", precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "priority")
    private Integer priority = 0;

    @Column(name = "banner_image")
    private String bannerImage;

    @Column(name = "badge_text")
    @Size(max = 30)
    private String badgeText;

    @Column(name = "badge_color")
    @Size(max = 7)
    private String badgeColor;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "promotion_products", joinColumns = @JoinColumn(name = "promotion_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> products = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "promotion_categories", joinColumns = @JoinColumn(name = "promotion_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    // ================== Constructors ==================

    public Promotion() {
        this.active = true;
        this.priority = 0;
        this.products = new HashSet<>();
        this.categories = new HashSet<>();
    }

    public Promotion(String name, String description, DiscountType type, BigDecimal value,
            LocalDateTime startDate, LocalDateTime endDate, boolean active,
            Integer priority, String bannerImage, String badgeText, String badgeColor,
            Set<Product> products, Set<Category> categories) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.value = value;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.priority = priority != null ? priority : 0;
        this.bannerImage = bannerImage;
        this.badgeText = badgeText;
        this.badgeColor = badgeColor;
        this.products = products != null ? products : new HashSet<>();
        this.categories = categories != null ? categories : new HashSet<>();
    }

    // ================== Getters and Setters ==================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getBannerImage() {
        return bannerImage;
    }

    public void setBannerImage(String bannerImage) {
        this.bannerImage = bannerImage;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public void setBadgeText(String badgeText) {
        this.badgeText = badgeText;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public void setBadgeColor(String badgeColor) {
        this.badgeColor = badgeColor;
    }

    public Set<Product> getProducts() {
        return products;
    }

    public void setProducts(Set<Product> products) {
        this.products = products;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    // ================== Business Methods ==================

    /**
     * Checks if the promotion is currently active.
     */
    public boolean isCurrentlyActive() {
        if (!active)
            return false;

        LocalDateTime now = LocalDateTime.now();
        if (startDate != null && now.isBefore(startDate))
            return false;
        if (endDate != null && now.isAfter(endDate))
            return false;

        return true;
    }

    /**
     * Calculates the promotional price for a product.
     */
    public BigDecimal calculatePromotionalPrice(BigDecimal originalPrice) {
        if (!isCurrentlyActive()) {
            return originalPrice;
        }

        return switch (type) {
            case FIXED_AMOUNT -> originalPrice.subtract(value).max(BigDecimal.ZERO);
            case PERCENTAGE -> originalPrice.subtract(
                    originalPrice.multiply(value).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
            case FIXED_PRICE -> value;
            default -> originalPrice;
        };
    }

    /**
     * Adds a product to the promotion.
     */
    public void addProduct(Product product) {
        products.add(product);
    }

    /**
     * Removes a product from the promotion.
     */
    public void removeProduct(Product product) {
        products.remove(product);
    }

    /**
     * Adds a category to the promotion.
     */
    public void addCategory(Category category) {
        categories.add(category);
    }

    /**
     * Checks if the promotion applies to a product.
     */
    public boolean appliesToProduct(Product product) {
        if (products.isEmpty() && categories.isEmpty()) {
            return false; // Promotion must have targets
        }

        if (products.contains(product)) {
            return true;
        }

        if (product.getCategory() != null && categories.contains(product.getCategory())) {
            return true;
        }

        return false;
    }
}
