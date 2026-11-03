package com.ecommerce.application.service;

import com.ecommerce.application.dto.cart.AddToCartRequest;
import com.ecommerce.application.dto.cart.CartDto;
import com.ecommerce.domain.cart.Cart;
import com.ecommerce.domain.cart.CartItem;
import com.ecommerce.domain.exception.EntityNotFoundException;
import com.ecommerce.domain.exception.InsufficientStockException;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.product.ProductStatus;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.CartRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for shopping cart operations.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CartDto getCart(User user) {
        Cart cart = getOrCreateCart(user);
        return toDto(cart);
    }

    @Transactional
    public CartDto getCartBySession(String sessionId) {
        Cart cart = cartRepository.findBySessionIdWithItems(sessionId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setSessionId(sessionId);
                    return cartRepository.save(newCart);
                });
        return toDto(cart);
    }

    @Transactional
    public CartDto addToCart(User user, AddToCartRequest request) {
        Cart cart = getOrCreateCart(user);
        return addItemToCart(cart, request);
    }

    @Transactional
    public CartDto addToCartBySession(String sessionId, AddToCartRequest request) {
        Cart cart = cartRepository.findBySessionIdWithItems(sessionId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setSessionId(sessionId);
                    return cartRepository.save(newCart);
                });
        return addItemToCart(cart, request);
    }

    private CartDto addItemToCart(Cart cart, AddToCartRequest request) {
        Product product = productRepository.findActiveById(UUID.fromString(request.getProductId()))
                .orElseThrow(() -> new EntityNotFoundException("Product", request.getProductId()));

        if (product.getAvailableQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(
                    product.getSku(),
                    request.getQuantity(),
                    product.getAvailableQuantity());
        }

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (product.getAvailableQuantity() < newQuantity) {
                throw new InsufficientStockException(product.getSku(), newQuantity, product.getAvailableQuantity());
            }
            existingItem.setQuantity(newQuantity);
            existingItem.updateUnitPrice();
        } else {
            CartItem newItem = new CartItem();
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            newItem.setUnitPrice(product.getPrice());
            cart.addItem(newItem);
        }

        cart = cartRepository.save(cart);
        log.info("Added product {} to cart (qty: {})", product.getSku(), request.getQuantity());

        return toDto(cart);
    }

    @Transactional
    public CartDto updateCartItem(User user, String itemId, int quantity) {
        Cart cart = getOrCreateCart(user);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().toString().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("CartItem", itemId));

        if (quantity <= 0) {
            cart.removeItem(item);
        } else {
            if (item.getProduct().getStatus() != ProductStatus.ACTIVE || item.getProduct().isDeleted()) {
                throw new EntityNotFoundException("Product", item.getProduct().getId());
            }
            if (item.getProduct().getAvailableQuantity() < quantity) {
                throw new InsufficientStockException(
                        item.getProduct().getSku(),
                        quantity,
                        item.getProduct().getAvailableQuantity());
            }
            item.setQuantity(quantity);
        }

        cart = cartRepository.save(cart);
        return toDto(cart);
    }

    @Transactional
    public CartDto removeFromCart(User user, String itemId) {
        Cart cart = getOrCreateCart(user);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().toString().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("CartItem", itemId));

        cart.removeItem(item);
        cart = cartRepository.save(cart);

        return toDto(cart);
    }

    @Transactional
    public void clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cart.clear();
        cartRepository.save(cart);
    }

    @Transactional
    public CartDto mergeCart(User user, String sessionId) {
        Cart userCart = getOrCreateCart(user);

        cartRepository.findBySessionIdWithItems(sessionId).ifPresent(guestCart -> {
            for (CartItem guestItem : guestCart.getItems()) {
                AddToCartRequest request = new AddToCartRequest(
                        guestItem.getProduct().getId().toString(),
                        guestItem.getQuantity());
                try {
                    addItemToCart(userCart, request);
                } catch (InsufficientStockException e) {
                    log.warn("Could not merge cart item due to insufficient stock: {}",
                            guestItem.getProduct().getSku());
                }
            }
            cartRepository.delete(guestCart);
        });

        return toDto(userCart);
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserIdWithItems(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    private CartDto toDto(Cart cart) {
        CartDto dto = new CartDto();
        dto.setId(cart.getId().toString());
        dto.setItems(cart.getItems().stream()
                .map(item -> {
                    CartDto.CartItemDto itemDto = new CartDto.CartItemDto();
                    itemDto.setId(item.getId().toString());
                    itemDto.setProductId(item.getProduct().getId().toString());
                    itemDto.setProductName(item.getProduct().getName());
                    itemDto.setProductSlug(item.getProduct().getSlug());
                    itemDto.setProductImage(item.getProduct().getPrimaryImageUrl());
                    itemDto.setProductSku(item.getProduct().getSku());
                    itemDto.setUnitPrice(item.getUnitPrice());
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setTotal(item.getTotal());
                    itemDto.setInStock(item.getProduct().isAvailable());
                    itemDto.setAvailableQuantity(item.getProduct().getAvailableQuantity());
                    return itemDto;
                })
                .collect(Collectors.toList()));
        dto.setDiscountCode(cart.getDiscountCode());
        dto.setDiscountAmount(cart.getDiscountAmount());
        dto.setSubtotal(cart.getSubtotal());
        dto.setTotal(cart.getTotal());
        dto.setItemCount(cart.getItemCount());
        return dto;
    }
}
