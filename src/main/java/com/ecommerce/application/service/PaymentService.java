package com.ecommerce.application.service;

import com.ecommerce.application.dto.payment.PayOrderRequest;
import com.ecommerce.application.dto.payment.PaymentDto;
import com.ecommerce.application.payment.PaymentGateway;
import com.ecommerce.application.payment.PaymentGateway.ChargeRequest;
import com.ecommerce.application.payment.PaymentGateway.ChargeResult;
import com.ecommerce.domain.exception.EntityNotFoundException;
import com.ecommerce.domain.exception.PaymentException;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentMethod;
import com.ecommerce.domain.payment.PaymentStatus;
import com.ecommerce.domain.user.Role;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Takes payment for an order and confirms it. One payment row per order: a declined attempt can be retried,
 * a completed one is returned again instead of charging twice.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Methods a provider charges online. Cash and bank transfer are settled outside checkout. */
    static final Set<PaymentMethod> ONLINE_METHODS = Set.of(
            PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD, PaymentMethod.DIGITAL_WALLET, PaymentMethod.PAYPAL);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentGateway gateway;

    public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
            OrderService orderService, PaymentGateway gateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.gateway = gateway;
    }

    /**
     * The order row is locked for the whole call, so a double click or a retried request cannot charge twice.
     * A declined payment is saved (and the order stays pending) before the 402 is returned.
     */
    @Transactional(noRollbackFor = PaymentException.class)
    public PaymentDto pay(UUID orderId, User customer, PayOrderRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        if (customer == null || order.getUser() == null || !order.getUser().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Only the customer who placed the order can pay for it");
        }

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.COMPLETED) {
            return PaymentDto.from(payment);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be paid; this order is " + order.getStatus());
        }
        if (!ONLINE_METHODS.contains(request.method())) {
            throw new IllegalArgumentException("Payment method " + request.method() + " is not taken online");
        }

        if (payment == null) {
            payment = new Payment();
            payment.setOrder(order);
        }
        payment.setAmount(order.getTotal());
        payment.setCurrency(order.getCurrency());
        payment.setMethod(request.method());
        payment.setProvider(gateway.name());
        payment.setStatus(PaymentStatus.PROCESSING);

        ChargeResult result;
        try {
            result = gateway.charge(new ChargeRequest(order.getOrderNumber(), order.getTotal(), order.getCurrency(),
                    request.method(), request.paymentToken()));
        } catch (RuntimeException e) {
            log.error("Payment provider error for order {}", order.getOrderNumber(), e);
            result = ChargeResult.declined("provider_error", "The payment provider could not be reached.");
        }

        if (!result.approved()) {
            payment.fail(result.failureReason(), result.failureCode());
            paymentRepository.save(payment);
            log.info("Payment declined for order {}: {}", order.getOrderNumber(), result.failureCode());
            throw new PaymentException("Payment declined: " + result.failureReason(), result.failureCode());
        }

        payment.setFailureCode(null);
        payment.setFailureReason(null);
        payment.setFailedAt(null);
        payment.setProviderTransactionId(result.providerTransactionId());
        payment.setCardLastFour(result.cardLastFour());
        payment.complete(Payment.generateTransactionId());
        payment = paymentRepository.save(payment);

        orderService.confirmOrder(orderId);
        log.info("Order {} paid and confirmed", order.getOrderNumber());
        return PaymentDto.from(payment);
    }

    /** The payment for an order, to its customer or an administrator. */
    @Transactional(readOnly = true)
    public PaymentDto getPayment(UUID orderId, User currentUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        boolean admin = currentUser != null && currentUser.getRole() == Role.ADMIN;
        boolean owner = currentUser != null && order.getUser() != null
                && order.getUser().getId().equals(currentUser.getId());
        if (!admin && !owner) {
            throw new AccessDeniedException("You cannot access this order");
        }
        return paymentRepository.findByOrderId(orderId).map(PaymentDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Payment for order", orderId));
    }
}
