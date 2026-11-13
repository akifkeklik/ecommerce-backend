package com.ecommerce.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("Only confirmed orders can enter processing")
    void startProcessing_RequiresConfirmedOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.CONFIRMED);

        order.startProcessing();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("Pending orders cannot skip confirmation")
    void startProcessing_WhenPending_ThrowsException() {
        Order order = new Order();

        assertThatThrownBy(order::startProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");
    }

    @Test
    @DisplayName("Orders can move from shipped to out for delivery")
    void markOutForDelivery_RequiresShippedOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);

        order.markOutForDelivery();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("Orders cannot be marked out for delivery before shipment")
    void markOutForDelivery_WhenProcessing_ThrowsException() {
        Order order = new Order();
        order.setStatus(OrderStatus.PROCESSING);

        assertThatThrownBy(order::markOutForDelivery)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipped");
    }

    @Test
    @DisplayName("Orders can be delivered after shipment")
    void deliver_WhenShipped_MarksOrderDelivered() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);

        order.deliver();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Orders cannot be delivered before shipment")
    void deliver_WhenProcessing_ThrowsException() {
        Order order = new Order();
        order.setStatus(OrderStatus.PROCESSING);

        assertThatThrownBy(order::deliver)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipped");
    }

    @Test
    @DisplayName("Generated order numbers are unique and API-safe")
    void generateOrderNumber_ProducesUniqueReferences() {
        java.util.Set<String> references = new java.util.HashSet<>();

        for (int index = 0; index < 10_000; index++) {
            String reference = Order.generateOrderNumber();
            assertThat(reference).matches("ORD-[A-F0-9]{16}");
            references.add(reference);
        }

        assertThat(references).hasSize(10_000);
    }

}
