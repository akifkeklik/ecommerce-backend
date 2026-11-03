package com.ecommerce.application.service;

import com.ecommerce.application.dto.common.AddressDto;
import com.ecommerce.application.dto.common.PagedResponse;
import com.ecommerce.application.dto.order.CreateOrderRequest;
import com.ecommerce.application.dto.order.OrderDto;
import com.ecommerce.domain.exception.EntityNotFoundException;
import com.ecommerce.domain.exception.InsufficientStockException;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderItem;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.product.Product;
import com.ecommerce.domain.user.Address;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for order operations.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * Creates a new order.
     */
    @Transactional
    public OrderDto createOrder(User user, CreateOrderRequest request) {
        // Build order
        Order order = new Order();
        order.setOrderNumber(Order.generateOrderNumber());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(toAddress(request.getShippingAddress()));
        order.setBillingAddress(request.getBillingAddress() != null
                ? toAddress(request.getBillingAddress())
                : toAddress(request.getShippingAddress()));
        order.setDiscountCode(request.getDiscountCode());
        order.setNotes(request.getNotes());

        BigDecimal subtotal = BigDecimal.ZERO;

        // Process items
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findActiveByIdForUpdate(UUID.fromString(itemRequest.getProductId()))
                    .orElseThrow(() -> new EntityNotFoundException("Product", itemRequest.getProductId()));

            // Check and reserve stock
            if (product.getAvailableQuantity() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                        product.getSku(),
                        itemRequest.getQuantity(),
                        product.getAvailableQuantity());
            }
            product.reserveStock(itemRequest.getQuantity());
            productRepository.save(product);

            // Create order item
            OrderItem orderItem = OrderItem.fromProduct(product, itemRequest.getQuantity());
            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getTotal());
        }

        // Set totals
        order.setSubtotal(subtotal);
        order.setTotal(
                subtotal.add(order.getShippingAmount()).add(order.getTaxAmount()).subtract(order.getDiscountAmount()));

        order = orderRepository.save(order);
        log.info("Order created: {} for user: {}", order.getOrderNumber(), user.getUsername());

        return toDto(order);
    }

    /**
     * Gets an order by ID.
     */
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        return toDto(order);
    }

    /** Returns an order only to its owner or an administrator. */
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        assertCanAccessOrder(order, currentUser);
        return toDto(order);
    }

    /**
     * Gets an order by order number.
     */
    @Transactional(readOnly = true)
    public OrderDto getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderNumber));
        return toDto(order);
    }

    /** Returns an order by number only to its owner or an administrator. */
    @Transactional(readOnly = true)
    public OrderDto getOrderByNumber(String orderNumber, User currentUser) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderNumber));
        assertCanAccessOrder(order, currentUser);
        return toDto(order);
    }

    /**
     * Gets orders for a user.
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> getUserOrders(User user, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserId(user.getId(), pageable);
        return PagedResponse.of(orders.map(this::toDto));
    }

    /**
     * Confirms an order after payment.
     */
    @Transactional
    public OrderDto confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        order.confirm();

        // Confirm stock reduction
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.confirmSale(item.getQuantity());
            productRepository.save(product);
        }

        order = orderRepository.save(order);
        log.info("Order confirmed: {}", order.getOrderNumber());

        return toDto(order);
    }

    /**
     * Cancels an order.
     */
    @Transactional
    public OrderDto cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        return cancelOrderEntity(order, reason);
    }

    /** A customer may cancel only their own order; administrators may cancel any order. */
    @Transactional
    public OrderDto cancelOrder(UUID orderId, String reason, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        assertCanAccessOrder(order, currentUser);
        return cancelOrderEntity(order, reason);
    }

    private OrderDto cancelOrderEntity(Order order, String reason) {
        if (!order.isCancellable()) {
            throw new IllegalStateException("Order cannot be cancelled in current state");
        }

        order.cancel(reason);

        // Release reserved stock
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.releaseStock(item.getQuantity());
            productRepository.save(product);
        }

        order = orderRepository.save(order);
        log.info("Order cancelled: {} - Reason: {}", order.getOrderNumber(), reason);
        return toDto(order);
    }

    private void assertCanAccessOrder(Order order, User currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }

        boolean isAdmin = currentUser.getRole() == com.ecommerce.domain.user.Role.ADMIN;
        boolean isOwner = order.getUser() != null
                && order.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("You cannot access this order");
        }
    }

    /**
     * Updates order status.
     */
    @Transactional
    public OrderDto updateOrderStatus(UUID orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        order.setStatus(status);
        order = orderRepository.save(order);

        return toDto(order);
    }

    private Address toAddress(AddressDto dto) {
        return new Address(
                dto.getStreetAddress(),
                dto.getAddressLine2(),
                dto.getCity(),
                dto.getState(),
                dto.getPostalCode(),
                dto.getCountry(),
                dto.getPhoneNumber());
    }

    private AddressDto toAddressDto(Address address) {
        if (address == null)
            return null;
        return new AddressDto(
                address.getStreetAddress(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getPhoneNumber());
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId().toString());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setStatus(order.getStatus().name());
        dto.setItems(order.getItems().stream()
                .map(item -> {
                    OrderDto.OrderItemDto itemDto = new OrderDto.OrderItemDto();
                    itemDto.setId(item.getId().toString());
                    itemDto.setProductId(item.getProduct() != null ? item.getProduct().getId().toString() : null);
                    itemDto.setProductName(item.getProductName());
                    itemDto.setProductSku(item.getProductSku());
                    itemDto.setProductImage(item.getProductImage());
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setUnitPrice(item.getUnitPrice());
                    itemDto.setDiscountAmount(item.getDiscountAmount());
                    itemDto.setTotal(item.getTotal());
                    return itemDto;
                })
                .collect(Collectors.toList()));
        dto.setShippingAddress(toAddressDto(order.getShippingAddress()));
        dto.setBillingAddress(toAddressDto(order.getBillingAddress()));
        dto.setSubtotal(order.getSubtotal());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setDiscountCode(order.getDiscountCode());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setShippingAmount(order.getShippingAmount());
        dto.setTotal(order.getTotal());
        dto.setCurrency(order.getCurrency());
        dto.setNotes(order.getNotes());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setCompletedAt(order.getCompletedAt());
        dto.setItemCount(order.getItemCount());
        return dto;
    }
}
