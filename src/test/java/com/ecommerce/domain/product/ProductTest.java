package com.ecommerce.domain.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Product entity business logic.
 */
class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setName("Test Product");
        product.setSku("TEST-001");
        product.setSlug("test-product");
        product.setPrice(new BigDecimal("99.99"));
        product.setCompareAtPrice(new BigDecimal("129.99"));
        product.setStockQuantity(100);
        product.setReservedQuantity(10);
        product.setLowStockThreshold(15);
        product.setStatus(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should calculate available quantity correctly")
    void getAvailableQuantity_ReturnsStockMinusReserved() {
        assertThat(product.getAvailableQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("Should return true when product is in stock")
    void isInStock_WhenAvailable_ReturnsTrue() {
        assertThat(product.isInStock()).isTrue();
    }

    @Test
    @DisplayName("Should return false when product is out of stock")
    void isInStock_WhenNotAvailable_ReturnsFalse() {
        product.setStockQuantity(10);
        product.setReservedQuantity(10);

        assertThat(product.isInStock()).isFalse();
    }

    @Test
    @DisplayName("Should detect low stock correctly")
    void isLowStock_WhenBelowThreshold_ReturnsTrue() {
        product.setStockQuantity(20);
        product.setReservedQuantity(10); // Available = 10, threshold = 15

        assertThat(product.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void reserveStock_ValidQuantity_UpdatesReservedQuantity() {
        product.reserveStock(20);

        assertThat(product.getReservedQuantity()).isEqualTo(30);
        assertThat(product.getAvailableQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("Should throw exception when reserving more than available")
    void reserveStock_InsufficientStock_ThrowsException() {
        assertThatThrownBy(() -> product.reserveStock(100))
                .isInstanceOf(com.ecommerce.domain.exception.InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("Should release stock correctly")
    void releaseStock_ValidQuantity_UpdatesReservedQuantity() {
        product.releaseStock(5);

        assertThat(product.getReservedQuantity()).isEqualTo(5);
        assertThat(product.getAvailableQuantity()).isEqualTo(95);
    }

    @Test
    @DisplayName("Should confirm sale and update quantities")
    void confirmSale_UpdatesStockAndSales() {
        int quantitySold = 5;
        product.confirmSale(quantitySold);

        assertThat(product.getStockQuantity()).isEqualTo(95);
        assertThat(product.getReservedQuantity()).isEqualTo(5);
        assertThat(product.getTotalSales()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should calculate discount percentage correctly")
    void getDiscountPercentage_WithCompareAtPrice_ReturnsDiscount() {
        // 99.99 / 129.99 = ~23% discount
        assertThat(product.getDiscountPercentage()).isEqualTo(23);
    }

    @Test
    @DisplayName("Should return zero discount when no compare price")
    void getDiscountPercentage_NoComparePrice_ReturnsZero() {
        product.setCompareAtPrice(null);

        assertThat(product.getDiscountPercentage()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should update average rating correctly")
    void updateRating_CalculatesNewAverage() {
        product.setAverageRating(new BigDecimal("4.00"));
        product.setReviewCount(4);

        product.updateRating(new BigDecimal("5.00"));

        assertThat(product.getReviewCount()).isEqualTo(5);
        assertThat(product.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.20"));
    }

    @Test
    @DisplayName("Should check availability correctly")
    void isAvailable_ActiveAndInStock_ReturnsTrue() {
        assertThat(product.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Should return false for unavailable product")
    void isAvailable_InactiveStatus_ReturnsFalse() {
        product.setStatus(ProductStatus.DRAFT);

        assertThat(product.isAvailable()).isFalse();
    }
}
