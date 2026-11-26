package com.ecommerce.application.service;

import com.ecommerce.application.dto.payment.PayOrderRequest;
import com.ecommerce.application.dto.payment.PaymentDto;
import com.ecommerce.application.payment.PaymentGateway;
import com.ecommerce.application.payment.PaymentGateway.ChargeResult;
import com.ecommerce.domain.exception.PaymentException;
import com.ecommerce.domain.order.Order;
import com.ecommerce.domain.order.OrderStatus;
import com.ecommerce.domain.payment.Payment;
import com.ecommerce.domain.payment.PaymentMethod;
import com.ecommerce.domain.payment.PaymentStatus;
import com.ecommerce.domain.user.User;
import com.ecommerce.infrastructure.payment.SimulatedPaymentGateway;
import com.ecommerce.infrastructure.repository.OrderRepository;
import com.ecommerce.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderService orderService;

    private PaymentGateway gateway;
    private PaymentService service;
    private User customer;
    private Order order;

    @BeforeEach
    void setUp() {
        gateway = spy(new SimulatedPaymentGateway());
        service = new PaymentService(orderRepository, paymentRepository, orderService, gateway);

        customer = new User();
        customer.setId(UUID.randomUUID());
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-1");
        order.setUser(customer);
        order.setTotal(new BigDecimal("120.50"));

        lenient().when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        lenient().when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PayOrderRequest card(String token) {
        return new PayOrderRequest(PaymentMethod.CREDIT_CARD, token);
    }

    @Test
    @DisplayName("An approved charge stores a completed payment for the order total and confirms the order")
    void approved_completesAndConfirms() {
        PaymentDto dto = service.pay(order.getId(), customer, card("tok_visa"));

        assertThat(dto.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(dto.amount()).isEqualByComparingTo("120.50");
        assertThat(dto.provider()).isEqualTo("simulated");
        assertThat(dto.cardLastFour()).isEqualTo("4242");
        verify(orderService).confirmOrder(order.getId());
    }

    @Test
    @DisplayName("A declined charge is saved as failed, leaves the order pending and returns 402")
    void declined_isSavedAndOrderStaysPending() {
        assertThatThrownBy(() -> service.pay(order.getId(), customer, card("tok_decline")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("declined");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureCode()).isEqualTo("card_declined");
        verify(orderService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("A failed attempt can be retried on the same payment row")
    void retryAfterDecline_reusesRow() {
        Payment failed = new Payment();
        failed.setOrder(order);
        failed.fail("The card was declined.", "card_declined");
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(failed));

        PaymentDto dto = service.pay(order.getId(), customer, card("tok_visa"));

        assertThat(dto.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(failed.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("Paying a paid order again returns the same payment and charges nothing")
    void alreadyPaid_isIdempotent() {
        Payment done = new Payment();
        done.setOrder(order);
        done.setAmount(order.getTotal());
        done.complete("TXN-1");
        order.setStatus(OrderStatus.CONFIRMED);
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(done));

        assertThat(service.pay(order.getId(), customer, card("tok_visa")).transactionId()).isEqualTo("TXN-1");
        verify(gateway, never()).charge(any());
        verify(orderService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("Only the customer who placed the order can pay for it")
    void otherCustomer_isRejected() {
        User other = new User();
        other.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.pay(order.getId(), other, card("tok_visa")))
                .isInstanceOf(AccessDeniedException.class);
        verify(gateway, never()).charge(any());
    }

    @Test
    @DisplayName("Cancelled orders and offline methods cannot be paid online")
    void invalidStateOrMethod_isRejected() {
        assertThatThrownBy(() -> service.pay(order.getId(), customer,
                new PayOrderRequest(PaymentMethod.CASH_ON_DELIVERY, null)))
                .isInstanceOf(IllegalArgumentException.class);

        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.pay(order.getId(), customer, card("tok_visa")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending");
        verify(gateway, never()).charge(any());
    }

    @Test
    @DisplayName("A provider outage is recorded as a failed payment, not a server error")
    void providerError_isAFailedPayment() {
        doThrow(new RuntimeException("timeout")).when(gateway).charge(any());

        assertThatThrownBy(() -> service.pay(order.getId(), customer, card("tok_visa")))
                .isInstanceOf(PaymentException.class);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("The simulated provider declines its test tokens and approves anything else")
    void simulatedGateway_testTokens() {
        SimulatedPaymentGateway simulated = new SimulatedPaymentGateway();
        var request = new PaymentGateway.ChargeRequest("ORD-1", BigDecimal.TEN, "USD", PaymentMethod.CREDIT_CARD, null);
        assertThat(simulated.charge(request).approved()).isTrue();

        ChargeResult funds = simulated.charge(new PaymentGateway.ChargeRequest("ORD-1", BigDecimal.TEN, "USD",
                PaymentMethod.CREDIT_CARD, "tok_insufficient_funds"));
        assertThat(funds.approved()).isFalse();
        assertThat(funds.failureCode()).isEqualTo("insufficient_funds");
    }
}
