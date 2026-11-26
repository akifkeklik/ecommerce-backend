package com.ecommerce.application.service;

import com.ecommerce.application.payment.PaymentGateway;
import com.ecommerce.application.payment.PaymentGateway.RefundResult;
import com.ecommerce.domain.exception.PaymentException;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentStatus;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.PaymentRepository;
import com.ecommerce.infrastructure.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private DiscountService discountService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;

    private OrderService service;
    private Order order;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, productRepository, discountService, paymentRepository,
                paymentGateway);
        customer = new User();
        customer.setId(UUID.randomUUID());
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-2");
        order.setUser(customer);
        order.setTotal(new BigDecimal("80.00"));
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Payment completedPayment() {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("80.00"));
        payment.setCurrency("USD");
        payment.setProviderTransactionId("sim_ch_1");
        payment.complete("TXN-2");
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    @DisplayName("Cancelling a paid order refunds the full amount and gives the discount use back")
    void cancelPaidOrder_refunds() {
        Payment payment = completedPayment();
        order.setDiscountCode("SAVE10");
        when(paymentGateway.refund("sim_ch_1", new BigDecimal("80.00"), "USD"))
                .thenReturn(new RefundResult(true, "sim_re_1", null));

        service.cancelOrder(order.getId(), "changed my mind", customer);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("80.00");
        verify(discountService).release("SAVE10");
    }

    @Test
    @DisplayName("A refused refund fails the cancellation so it can be rolled back")
    void refusedRefund_failsCancellation() {
        Payment payment = completedPayment();
        when(paymentGateway.refund(eq("sim_ch_1"), any(), eq("USD")))
                .thenReturn(new RefundResult(false, null, "charge already disputed"));

        assertThatThrownBy(() -> service.cancelOrder(order.getId(), "x", customer))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("disputed");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("An unpaid pending order is cancelled without calling the provider")
    void cancelUnpaidOrder_noRefund() {
        order.setStatus(OrderStatus.PENDING);
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

        service.cancelOrder(order.getId(), "x", customer);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(paymentGateway, never()).refund(any(), any(), any());
    }
}
