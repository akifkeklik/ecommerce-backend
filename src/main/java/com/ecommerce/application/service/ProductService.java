package com.ecommerce.application.service;

import com.ecommerce.application.dto.common.PagedResponse;
import com.ecommerce.application.dto.product.CreateProductRequest;
import com.ecommerce.application.dto.product.ProductDto;
import com.ecommerce.domain.exception.EntityNotFoundException;
import com.ecommerce.domain.product.*;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.CategoryRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for product operations.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDto> getAllProducts(Pageable pageable) {
        Page<Product> products = productRepository.findActiveProducts(ProductStatus.ACTIVE, pageable);
        return PagedResponse.of(products.map(this::toDto));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductDto getProductById(UUID id) {
        Product product = productRepository.findActiveById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductBySlug(String slug) {
        Product product = productRepository.findActiveBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Product", slug));
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDto> getProductsByCategory(UUID categoryId, Pageable pageable) {
        Page<Product> products = productRepository.findActiveByCategory(categoryId, pageable);
        return PagedResponse.of(products.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductDto> searchProducts(String query, Pageable pageable) {
        Page<Product> products = productRepository.searchProducts(query, pageable);
        return PagedResponse.of(products.map(this::toDto));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "featured-products")
    public List<ProductDto> getFeaturedProducts() {
        return productRepository.findFeaturedProducts().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = { "products", "featured-products" }, allEntries = true)
    public ProductDto createProduct(CreateProductRequest request, User seller) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + request.getSku());
        }

        String slug = generateSlug(request.getName());

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(UUID.fromString(request.getCategoryId()))
                    .orElseThrow(() -> new EntityNotFoundException("Category", request.getCategoryId()));
        }

        Product product = new Product();
        product.setName(request.getName());
        product.setSlug(slug);
        product.setSku(request.getSku());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCompareAtPrice(request.getCompareAtPrice());
        product.setCostPrice(request.getCostPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setLowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 10);
        product.setCategory(category);
        product.setSeller(seller);
        product.setTags(request.getTags());
        product.setWeight(request.getWeight());
        product.setWeightUnit(request.getWeightUnit());
        product.setLength(request.getLength());
        product.setWidth(request.getWidth());
        product.setHeight(request.getHeight());
        product.setDimensionUnit(request.getDimensionUnit());
        product.setFeatured(request.isFeatured());
        product.setDigital(request.isDigital());
        product.setRequiresShipping(request.isRequiresShipping());
        product.setMetaTitle(request.getMetaTitle());
        product.setMetaDescription(request.getMetaDescription());
        product.setStatus(ProductStatus.DRAFT);

        product = productRepository.save(product);
        log.info("Product created: {} (SKU: {})", product.getName(), product.getSku());

        return toDto(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void updateStock(UUID productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        product.setStockQuantity(quantity);
        productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public void updateStock(UUID productId, int quantity, User currentUser) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        assertCanManageProduct(product, currentUser);
        product.setStockQuantity(quantity);
        productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = { "products", "featured-products" }, allEntries = true)
    public ProductDto publishProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        product.setStatus(ProductStatus.ACTIVE);
        return toDto(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = { "products", "featured-products" }, allEntries = true)
    public ProductDto publishProduct(UUID productId, User currentUser) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product", productId));
        assertCanManageProduct(product, currentUser);
        product.setStatus(ProductStatus.ACTIVE);
        return toDto(productRepository.save(product));
    }

    private void assertCanManageProduct(Product product, User currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }

        boolean isAdmin = currentUser.getRole() == com.ecommerce.domain.user.Role.ADMIN;
        boolean isOwner = product.getSeller() != null
                && product.getSeller().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot manage this product");
        }
    }

    private String generateSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();

        String slug = baseSlug;
        int counter = 1;
        while (productRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId().toString());
        dto.setName(product.getName());
        dto.setSlug(product.getSlug());
        dto.setSku(product.getSku());
        dto.setShortDescription(product.getShortDescription());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setCompareAtPrice(product.getCompareAtPrice());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setInStock(product.isInStock());
        dto.setStatus(product.getStatus().name());

        if (product.getCategory() != null) {
            dto.setCategory(new ProductDto.CategoryDto(
                    product.getCategory().getId().toString(),
                    product.getCategory().getName(),
                    product.getCategory().getSlug()));
        }

        if (product.getSeller() != null) {
            dto.setSeller(new ProductDto.SellerDto(
                    product.getSeller().getId().toString(),
                    product.getSeller().getUsername(),
                    product.getSeller().getAvatarUrl()));
        }

        dto.setImages(product.getImages().stream()
                .map(img -> new ProductDto.ImageDto(
                        img.getId().toString(),
                        img.getUrl(),
                        img.getAltText(),
                        img.isPrimary()))
                .collect(Collectors.toList()));

        dto.setTags(product.getTags());
        dto.setAverageRating(product.getAverageRating());
        dto.setReviewCount(product.getReviewCount());
        dto.setTotalSales(product.getTotalSales());
        dto.setFeatured(product.isFeatured());
        dto.setDigital(product.isDigital());
        dto.setRequiresShipping(product.isRequiresShipping());
        dto.setDiscountPercentage(product.getDiscountPercentage());
        dto.setCreatedAt(product.getCreatedAt());
        return dto;
    }
}
