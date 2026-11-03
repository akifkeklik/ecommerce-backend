package com.ecommerce.domain.product;

import com.ecommerce.domain.common.BaseEntity;
import com.ecommerce.domain.exception.InsufficientStockException;
import com.ecommerce.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Product aggregate root and JPA entity.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_category", columnList = "category_id"),
        @Index(name = "idx_product_seller", columnList = "seller_id"),
        @Index(name = "idx_product_status", columnList = "status"),
        @Index(name = "idx_product_price", columnList = "price")
})
public class Product extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 280)
    @Column(name = "slug", nullable = false, unique = true, length = 280)
    private String slug;

    @NotBlank
    @Size(max = 50)
    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Size(max = 500)
    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "low_stock_threshold")
    private int lowStockThreshold = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "weight_unit", length = 10)
    private String weightUnit = "kg";

    @Column(name = "length")
    private Double length;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Column(name = "dimension_unit", length = 10)
    private String dimensionUnit = "cm";

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count")
    private int reviewCount;

    @Column(name = "total_sales")
    private int totalSales;

    @Column(name = "is_featured")
    private boolean featured;

    @Column(name = "is_digital")
    private boolean digital;

    @Column(name = "requires_shipping")
    private boolean requiresShipping = true;

    @Size(max = 70)
    @Column(name = "meta_title", length = 70)
    private String metaTitle;

    @Size(max = 160)
    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags = new HashSet<>();

    /** Required by JPA and used by the application service. */
    public Product() {
        this.stockQuantity = 0;
        this.reservedQuantity = 0;
        this.lowStockThreshold = 10;
        this.status = ProductStatus.DRAFT;
        this.averageRating = BigDecimal.ZERO;
        this.requiresShipping = true;
        this.images = new ArrayList<>();
        this.tags = new HashSet<>();
    }

    public Product(String name, String slug, String sku, BigDecimal price, int stockQuantity) {
        this();
        setName(name);
        setSlug(Objects.requireNonNull(slug, "slug is required"));
        setSku(Objects.requireNonNull(sku, "sku is required"));
        setPrice(price);
        setStockQuantity(stockQuantity);
    }

    public void activate() {
        validatePrice(price);
        this.status = ProductStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
    }

    public void changePrice(BigDecimal newPrice) {
        setPrice(newPrice);
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.stockQuantity += quantity;
    }

    public void reserveStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (quantity > getAvailableQuantity()) {
            throw new InsufficientStockException(sku, quantity, getAvailableQuantity());
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
        Objects.requireNonNull(image, "image is required");
        image.setProduct(this);
        if (images.isEmpty()) image.setPrimary(true);
        images.add(image);
    }

    public void removeImage(ProductImage image) {
        if (images.remove(image)) image.setProduct(null);
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isBlank()) tags.add(tag.trim());
    }

    public void assignCategory(Category category) {
        this.category = Objects.requireNonNull(category);
    }

    public void assignSeller(User seller) {
        this.seller = Objects.requireNonNull(seller);
    }

    public void updateRating(BigDecimal newRating) {
        if (newRating == null || newRating.compareTo(BigDecimal.ZERO) < 0
                || newRating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Invalid rating");
        }
        BigDecimal total = averageRating.multiply(BigDecimal.valueOf(reviewCount));
        reviewCount++;
        averageRating = total.add(newRating)
                .divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP);
    }

    public int getAvailableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    public boolean isInStock() {
        return getAvailableQuantity() > 0;
    }

    public boolean isAvailable() {
        return status == ProductStatus.ACTIVE && isInStock();
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public int getDiscountPercentage() {
        if (compareAtPrice == null || price == null || compareAtPrice.compareTo(price) <= 0) return 0;
        return compareAtPrice.subtract(price)
                .divide(compareAtPrice, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Product name cannot be empty");
        this.name = name.trim();
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) {
        if (slug == null || slug.isBlank()) throw new IllegalArgumentException("Product slug cannot be empty");
        this.slug = slug.trim();
    }

    public String getSku() { return sku; }
    public void setSku(String sku) {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("Product SKU cannot be empty");
        this.sku = sku.trim();
    }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) {
        validatePrice(price);
        this.price = price;
    }

    public BigDecimal getCompareAtPrice() { return compareAtPrice; }
    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        if (compareAtPrice != null && price != null && compareAtPrice.compareTo(price) < 0) {
            throw new IllegalArgumentException("Compare price must be greater than actual price");
        }
        this.compareAtPrice = compareAtPrice;
    }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) {
        if (costPrice != null && costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost price cannot be negative");
        }
        this.costPrice = costPrice;
    }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) {
        if (stockQuantity < 0 || stockQuantity < reservedQuantity) {
            throw new IllegalArgumentException("Stock cannot be negative or lower than reserved quantity");
        }
        this.stockQuantity = stockQuantity;
    }

    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int reservedQuantity) {
        if (reservedQuantity < 0 || reservedQuantity > stockQuantity) {
            throw new IllegalArgumentException("Reserved quantity must be between zero and stock quantity");
        }
        this.reservedQuantity = reservedQuantity;
    }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) {
        if (lowStockThreshold < 0) throw new IllegalArgumentException("Low stock threshold cannot be negative");
        this.lowStockThreshold = lowStockThreshold;
    }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = Objects.requireNonNull(status); }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }
    public Double getLength() { return length; }
    public void setLength(Double length) { this.length = length; }
    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
    public String getDimensionUnit() { return dimensionUnit; }
    public void setDimensionUnit(String dimensionUnit) { this.dimensionUnit = dimensionUnit; }

    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) {
        if (averageRating == null || averageRating.compareTo(BigDecimal.ZERO) < 0
                || averageRating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Average rating must be between 0 and 5");
        }
        this.averageRating = averageRating;
    }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) {
        if (reviewCount < 0) throw new IllegalArgumentException("Review count cannot be negative");
        this.reviewCount = reviewCount;
    }

    public int getTotalSales() { return totalSales; }
    public void setTotalSales(int totalSales) {
        if (totalSales < 0) throw new IllegalArgumentException("Total sales cannot be negative");
        this.totalSales = totalSales;
    }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public boolean isDigital() { return digital; }
    public void setDigital(boolean digital) { this.digital = digital; }
    public boolean isRequiresShipping() { return requiresShipping; }
    public void setRequiresShipping(boolean requiresShipping) { this.requiresShipping = requiresShipping; }

    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String metaTitle) { this.metaTitle = metaTitle; }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = metaDescription; }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public String getPrimaryImageUrl() {
        return images.stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .map(ProductImage::getUrl)
                .orElseGet(() -> images.isEmpty() ? null : images.get(0).getUrl());
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void setTags(Set<String> tags) {
        this.tags.clear();
        if (tags != null) tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(tag -> !tag.isEmpty()).forEach(this.tags::add);
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid price");
        }
    }
}
