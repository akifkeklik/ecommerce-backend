package com.ecommerce.domain.product;

import com.ecommerce.domain.common.BaseEntity;
import com.ecommerce.domain.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Product extends BaseEntity {

    private String name;
    private String slug;
    private String sku;

    private BigDecimal price;
    private BigDecimal compareAtPrice;

    private int stockQuantity;
    private int reservedQuantity;
    private int lowStockThreshold;

    private ProductStatus status;

    private Category category;
    private User seller;

    private final List<ProductImage> images = new ArrayList<>();
    private final Set<String> tags = new HashSet<>();

    private BigDecimal averageRating;
    private int reviewCount;
    private int totalSales;

    private boolean featured;
    private boolean digital;
    private boolean requiresShipping;

    // ================== CONSTRUCTOR ==================

    public Product(String name, String slug, String sku, BigDecimal price, int stockQuantity) {
        validateName(name);
        validatePrice(price);

        this.name = name;
        this.slug = Objects.requireNonNull(slug);
        this.sku = Objects.requireNonNull(sku);
        this.price = price;

        this.stockQuantity = stockQuantity;
        this.reservedQuantity = 0;
        this.lowStockThreshold = 10;

        this.status = ProductStatus.DRAFT;

        this.averageRating = BigDecimal.ZERO;
        this.reviewCount = 0;
        this.totalSales = 0;

        this.requiresShipping = true;
    }

    // ================== BEHAVIOR ==================

    public void activate() {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Cannot activate product with invalid price");
        }
        this.status = ProductStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public void changePrice(BigDecimal newPrice) {
        validatePrice(newPrice);
        this.price = newPrice;
    }

    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        if (compareAtPrice != null && compareAtPrice.compareTo(price) < 0) {
            throw new IllegalArgumentException("Compare price must be greater than actual price");
        }
        this.compareAtPrice = compareAtPrice;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.stockQuantity += quantity;
    }

    public void reserveStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        if (quantity > getAvailableQuantity()) {
            throw new InsufficientStockException("Not enough stock");
        }

        this.reservedQuantity += quantity;
    }

    public void releaseStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public void confirmSale(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        if (quantity > reservedQuantity) {
            throw new IllegalStateException("Cannot confirm more than reserved");
        }

        this.stockQuantity -= quantity;
        this.reservedQuantity -= quantity;
        this.totalSales += quantity;
    }

    public void addImage(ProductImage image) {
        Objects.requireNonNull(image);

        image.setProduct(this);

        if (images.isEmpty()) {
            image.setPrimary(true);
        }

        images.add(image);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }

    public void addTag(String tag) {
        if (tag == null || tag.isBlank()) return;
        tags.add(tag);
    }

    public void assignCategory(Category category) {
        this.category = Objects.requireNonNull(category);
    }

    public void assignSeller(User seller) {
        this.seller = Objects.requireNonNull(seller);
    }

    public void updateRating(BigDecimal newRating) {
        if (newRating == null || newRating.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid rating");
        }

        BigDecimal total = averageRating.multiply(BigDecimal.valueOf(reviewCount));
        reviewCount++;

        averageRating = total.add(newRating)
                .divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP);
    }

    // ================== QUERY METHODS ==================

    public int getAvailableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE && getAvailableQuantity() > 0;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public int getDiscountPercentage() {
        if (compareAtPrice == null || compareAtPrice.compareTo(price) <= 0) return 0;

        return compareAtPrice.subtract(price)
                .divide(compareAtPrice, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    // ================== VALIDATION ==================

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid price");
        }
    }
}
